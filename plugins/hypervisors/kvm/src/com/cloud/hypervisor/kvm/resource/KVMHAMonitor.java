// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.hypervisor.kvm.resource;

import com.cloud.utils.script.Script;
import org.apache.log4j.Logger;
import org.libvirt.Connect;
import org.libvirt.LibvirtException;
import org.libvirt.StoragePool;
import org.libvirt.StoragePoolInfo.StoragePoolState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class KVMHAMonitor extends KVMHABase implements Runnable {
    private static final Logger s_logger = Logger.getLogger(KVMHAMonitor.class);

    static final int missingHeartbeatTimeout = 60 * 2;
    static final int missedHeartbeatCount = 5;
    private final Map<String, NfsStoragePool> _storagePool = new ConcurrentHashMap<String, NfsStoragePool>();
    AtomicBoolean running = new AtomicBoolean(true);
    private final Map<String, HeartbeatWriter> heartbeatWriters = new ConcurrentHashMap<>();

    private final String _hostIP; /* private ip address */

    public KVMHAMonitor(NfsStoragePool pool, String host, String scriptPath) {
        if (pool != null) {
            _storagePool.put(pool._poolUUID, pool);
            HeartbeatWriter writer = new HeartbeatWriter(pool._poolUUID);
            writer.start();
            heartbeatWriters.put(pool._poolUUID, writer);
        }
        _hostIP = host;
        configureHeartBeatPath(scriptPath);
    }

    private static synchronized void configureHeartBeatPath(String scriptPath) {
        KVMHABase.s_heartBeatPath = scriptPath;
    }

    public void addStoragePool(NfsStoragePool pool) {
        synchronized (_storagePool) {
            _storagePool.put(pool._poolUUID, pool);
        }
    }

    public void removeStoragePool(String uuid) {
        synchronized (_storagePool) {
            NfsStoragePool pool = _storagePool.get(uuid);
            if (pool != null) {
                Script.runSimpleBashScript("umount " + pool._mountDestPath);
                _storagePool.remove(uuid);
            }
        }
    }

    public List<NfsStoragePool> getStoragePools() {
        synchronized (_storagePool) {
            return new ArrayList<NfsStoragePool>(_storagePool.values());
        }
    }

    public NfsStoragePool getStoragePool(String uuid) {
        synchronized (_storagePool) {
            return _storagePool.get(uuid);
        }
    }

    String isUnHealthy() {
        long minUpdateForHealthy = System.currentTimeMillis() - 1000 * missingHeartbeatTimeout;
        List<String> pools = heartbeatWriters.values().stream()
                .filter(w -> w.getFailedAttempts() >= missedHeartbeatCount || w.isBehind(minUpdateForHealthy))
                .map(w -> w.poolUuid)
                .collect(Collectors.toList());
        if (pools.size() > 0) {
            return "Host is having heartbeat issues with the following pools: " + String.join(", ", pools);
        }
        return null;
    }

    private class HeartbeatWriter implements Runnable {
        private final Logger logger;
        private final Connect conn;
        private Thread worker;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicInteger failedAttempts = new AtomicInteger(0);
        private final String poolUuid;
        private long lastUpdateDone;

        HeartbeatWriter(String poolUuid) {
            this.poolUuid = poolUuid;
            logger = Logger.getLogger(HeartbeatWriter.class.getName() + "-" + poolUuid);
            try {
                this.conn = LibvirtConnection.getConnection();
            } catch (LibvirtException e) {
                logger.error("Unable to connect to libvirt, heartbeat stopping");
                throw new RuntimeException("Unable to connect to libvirt");
            }
        }

        void start() {
            logger.info("Starting heartbeat writer for pool: " + this.poolUuid);
            worker = new Thread(this);
            worker.start();
        }

        void stop() {
            internalStop();
            worker.interrupt();
        }

        private void internalStop() {
            logger.info("Stopping heartbeat writer for pool: " + this.poolUuid);
            running.set(false);
        }

        void join() throws InterruptedException {
            worker.join();
        }

        public int getFailedAttempts() {
            return this.failedAttempts.get();
        }

        public boolean isBehind(long checkTime) {
            return this.lastUpdateDone < checkTime;
        }

        public String getPoolUuid() {
            return this.poolUuid;
        }

        @Override
        public void run() {
            running.set(true);
            while (running.get()) {
                long start = System.currentTimeMillis();
                logger.debug("Checking to see if pool is still active");
                NfsStoragePool primaryStoragePool = getStoragePool(poolUuid);

                try {
                    StoragePool storage = conn.storagePoolLookupByUUIDString(poolUuid);
                    if (storage == null) {
                        logger.debug("Libvirt storage pool " + poolUuid + " not found, removing from HA list");
                        removeStoragePool(poolUuid);
                        internalStop();
                        continue;
                    } else if (storage.getInfo().state != StoragePoolState.VIR_STORAGE_POOL_RUNNING) {
                        logger.debug("Libvirt storage pool " + poolUuid + " found, but not running, removing from HA list");
                        removeStoragePool(poolUuid);
                        internalStop();
                        continue;
                    }
                    logger.debug("Found NFS storage pool " + poolUuid + " in libvirt, continuing");
                } catch (LibvirtException e) {
                    logger.debug("Failed to lookup libvirt storage pool " + poolUuid + " due to: " + e);

                    // we only want to remove pool if it's not found, not if libvirt connection fails
                    if (e.toString().contains("pool not found")) {
                        logger.debug("removing pool from HA monitor since it was deleted");
                        removeStoragePool(poolUuid);
                        internalStop();
                        continue;
                    }
                }

                Script cmd = new Script(s_heartBeatPath, _heartBeatUpdateTimeout, logger);
                cmd.add("-i", primaryStoragePool._poolIp);
                cmd.add("-p", primaryStoragePool._poolMountSourcePath);
                cmd.add("-m", primaryStoragePool._mountDestPath);
                cmd.add("-h", _hostIP);
                String result = cmd.execute();
                if (result != null) {
                    int attempts = this.failedAttempts.getAndIncrement();
                    logger.warn("write heartbeat failed: " + result + ", try: " + (attempts + 1));
                } else {
                    this.failedAttempts.set(0);
                }
                lastUpdateDone = System.currentTimeMillis();

                long remaining = start + _heartBeatUpdateFreq - System.currentTimeMillis();
                if (remaining >= 0) {
                    while (remaining > 0) {
                        try {
                            // Sleep is getting interrupted when script times out. Let's loop until it sorts out.
                            Thread.sleep(remaining);
                        } catch (InterruptedException e) {
                            logger.debug("[ignored] interupted between heartbeats.");
                        }
                        remaining = start + _heartBeatUpdateFreq - System.currentTimeMillis();
                    }
                } else {
                    logger.error("Running behind on heartbeat by " + (-remaining / 1000) + " seconds");
                }
            }
            heartbeatWriters.remove(poolUuid);
            logger.info("Heartbeat writer has stopped for " + this.poolUuid);
        }
    }

    @Override
    public void run() {
        s_logger.info("Starting KVM HA Monitor");
        while (running.get()) {
            Set<String> writers = heartbeatWriters.keySet();
            Set<String> pools = _storagePool.keySet();
            if (!writers.containsAll(pools)) {
                s_logger.info("Pool missing heartbeat writer");
                Map<String, HeartbeatWriter> newWriters = pools.stream()
                        .filter(pool -> !writers.contains(pool))
                        .map(HeartbeatWriter::new)
                        .peek(HeartbeatWriter::start)
                        .collect(Collectors.toMap(HeartbeatWriter::getPoolUuid, Function.identity()));
                heartbeatWriters.putAll(newWriters);
            } else if (!pools.containsAll(writers)) {
                s_logger.info("Writer for pool that doesn't exist found");
                List<HeartbeatWriter> stoppedWriters = writers.stream()
                        .filter(w -> !pools.contains(w))
                        .map(heartbeatWriters::get)
                        .peek(HeartbeatWriter::stop)
                        .collect(Collectors.toList());
                stoppedWriters.forEach(w -> heartbeatWriters.remove(w.poolUuid));
            }

            try {
                Thread.sleep(_heartBeatUpdateFreq);
            } catch (InterruptedException e) {
                s_logger.debug("[ignored] interupted between cleanups of heartbeats");
            }
        }
        s_logger.info("Shutting down KVM HA Monitor");
    }
}
