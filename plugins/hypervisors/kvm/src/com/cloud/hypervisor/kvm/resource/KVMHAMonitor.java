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
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.log4j.Logger;
import org.libvirt.Connect;
import org.libvirt.LibvirtException;
import org.libvirt.StoragePool;
import org.libvirt.StoragePoolInfo.StoragePoolState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class KVMHAMonitor extends KVMHABase implements Runnable {
    private static final Logger s_logger = Logger.getLogger(KVMHAMonitor.class);
    private final Map<String, NfsStoragePool> _storagePool = new ConcurrentHashMap<String, NfsStoragePool>();
    private final Map<String, Integer> poolHeartbeatFailedAttempts = new ConcurrentHashMap<>();
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

    public Set<String> getStoragePoolUuids() {
        synchronized (_storagePool) {
            return _storagePool.keySet();
        }
    }

    private class Monitor extends ManagedContextRunnable {

        @Override
        protected void runInContext() {
            Set<String> removedPools = new HashSet<String>();
            for (String uuid : getStoragePoolUuids()) {
                NfsStoragePool primaryStoragePool = getStoragePool(uuid);

                // check for any that have been deregistered with libvirt and
                // skip,remove them

                StoragePool storage = null;
                try {
                    Connect conn = LibvirtConnection.getConnection();
                    storage = conn.storagePoolLookupByUUIDString(uuid);
                    if (storage == null) {
                        s_logger.debug("Libvirt storage pool " + uuid + " not found, removing from HA list");
                        removedPools.add(uuid);
                        continue;

                    } else if (storage.getInfo().state != StoragePoolState.VIR_STORAGE_POOL_RUNNING) {
                        s_logger.debug("Libvirt storage pool " + uuid + " found, but not running, removing from HA list");

                        removedPools.add(uuid);
                        continue;
                    }
                    s_logger.debug("Found NFS storage pool " + uuid + " in libvirt, continuing");

                } catch (LibvirtException e) {
                    s_logger.debug("Failed to lookup libvirt storage pool " + uuid + " due to: " + e);

                    // we only want to remove pool if it's not found, not if libvirt
                    // connection fails
                    if (e.toString().contains("pool not found")) {
                        s_logger.debug("removing pool from HA monitor since it was deleted");
                        removedPools.add(uuid);
                        continue;
                    }
                }

                String result = null;
                // Try multiple times, but sleep in between tries to ensure it isn't a short lived transient error
//                for (int i = 1; i <= _heartBeatUpdateMaxTries; i++) {
                s_logger.debug("Attempting to write heartbeat file for pool path: " + primaryStoragePool._mountDestPath);
                Script cmd = new Script(s_heartBeatPath, _heartBeatUpdateTimeout, s_logger);
                cmd.add("-i", primaryStoragePool._poolIp);
                cmd.add("-p", primaryStoragePool._poolMountSourcePath);
                cmd.add("-m", primaryStoragePool._mountDestPath);
                cmd.add("-h", _hostIP);
                result = cmd.execute();
                if (result != null) {
                    int attempts = poolHeartbeatFailedAttempts.getOrDefault(primaryStoragePool._poolUUID, 0);
                    s_logger.warn("write heartbeat failed: " + result + ", try: " + attempts + " of " + _heartBeatUpdateMaxTries);
                    poolHeartbeatFailedAttempts.put(primaryStoragePool._poolUUID, attempts + 1);
//                        try {
//                            Thread.sleep(_heartBeatUpdateRetrySleep);
//                        } catch (InterruptedException e) {
//                            s_logger.debug("[ignored] interupted between heartbeat retries.");
//                        }
                }
//                    else {
//                        break;
//                    }
//                }

//                if (result != null) {
//                    // Stop cloudstack-agent if can't write to heartbeat file.
//                    // This will raise an alert on the mgmt server
//                    s_logger.warn("write heartbeat failed: " + result + "; stopping cloudstack-agent");
//                    Script cmd = new Script(s_heartBeatPath, _heartBeatUpdateTimeout, s_logger);
//                    cmd.add("-i", primaryStoragePool._poolIp);
//                    cmd.add("-p", primaryStoragePool._poolMountSourcePath);
//                    cmd.add("-m", primaryStoragePool._mountDestPath);
//                    cmd.add("-c");
//                    result = cmd.execute();
//                }
            }

            if (!removedPools.isEmpty()) {
                for (String uuid : removedPools) {
                    removeStoragePool(uuid);
                }
            }

        }
    }

    private class WriteHeartbeat implements Callable<Boolean> {
        private String poolUuid;

        WriteHeartbeat(String poolUuid) {
            this.poolUuid = poolUuid;
        }

        @Override
        public Boolean call() {
            NfsStoragePool primaryStoragePool = getStoragePool(poolUuid);

            // check for any that have been deregistered with libvirt and skip,remove them
            try {
                Connect conn = LibvirtConnection.getConnection();
                StoragePool storage = conn.storagePoolLookupByUUIDString(poolUuid);
                if (storage == null) {
                    s_logger.debug("Libvirt storage pool " + poolUuid + " not found, removing from HA list");
                    removeStoragePool(poolUuid);
                    return true;
                } else if (storage.getInfo().state != StoragePoolState.VIR_STORAGE_POOL_RUNNING) {
                    s_logger.debug("Libvirt storage pool " + poolUuid + " found, but not running, removing from HA list");
                    removeStoragePool(poolUuid);
                    return true;
                }
                s_logger.debug("Found NFS storage pool " + poolUuid + " in libvirt, continuing");

            } catch (LibvirtException e) {
                s_logger.debug("Failed to lookup libvirt storage pool " + poolUuid + " due to: " + e);

                // we only want to remove pool if it's not found, not if libvirt
                // connection fails
                if (e.toString().contains("pool not found")) {
                    s_logger.debug("removing pool from HA monitor since it was deleted");
                    removeStoragePool(poolUuid);
                    return true;
                }
            }

            String result;
            s_logger.debug("Attempting to write heartbeat file for pool path: " + primaryStoragePool._mountDestPath);
            Script cmd = new Script(s_heartBeatPath, _heartBeatUpdateTimeout, s_logger);
            cmd.add("-i", primaryStoragePool._poolIp);
            cmd.add("-p", primaryStoragePool._poolMountSourcePath);
            cmd.add("-m", primaryStoragePool._mountDestPath);
            cmd.add("-h", _hostIP);
            result = cmd.execute();
            if (result != null) {
                int attempts = poolHeartbeatFailedAttempts.getOrDefault(primaryStoragePool._poolUUID, 0);
                s_logger.warn("write heartbeat failed: " + result + ", try: " + attempts + " of " + _heartBeatUpdateMaxTries);
                poolHeartbeatFailedAttempts.put(primaryStoragePool._poolUUID, attempts + 1);
            }
            return false;
        }
    }

    private class HeartbeatWriter implements Runnable {
        private final Logger logger;
        private final Connect conn;
        private Thread worker;
        private final AtomicBoolean poolRunning = new AtomicBoolean(false);
        private final AtomicInteger attempts = new AtomicInteger(0);
        private final String poolUuid;

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
            poolRunning.set(false);
        }

        void join() throws InterruptedException {
            worker.join();
        }

        public int getFailedAttempts() {
            return this.attempts.get();
        }

        public String getPoolUuid() {
            return this.poolUuid;
        }

        @Override
        public void run() {
            poolRunning.set(true);
            while (poolRunning.get()) {
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
                    int attempts = this.attempts.getAndIncrement();
                    logger.warn("write heartbeat failed: " + result + ", try: " + (attempts + 1) + " of " + _heartBeatUpdateMaxTries);
                } else {
                    this.attempts.set(0);
                }

                long remaining = start + _heartBeatUpdateFreq - System.currentTimeMillis();
                if (remaining > 0) {
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
                    logger.error("Running behind on heartbeat by " + (-remaining/1000) + " seconds");
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
                Map<String, HeartbeatWriter> newWriters = pools.stream().filter(pool -> !writers.contains(pool)).map(HeartbeatWriter::new).peek(HeartbeatWriter::start).collect(Collectors.toMap(HeartbeatWriter::getPoolUuid, Function.identity()));
                heartbeatWriters.putAll(newWriters);
            } else if (!pools.containsAll(writers)) {
                s_logger.info("Writer for pool that doesn't exist found");
                List<HeartbeatWriter> stoppedWriters = writers.stream().filter(w -> !pools.contains(w)).map(heartbeatWriters::get).peek(HeartbeatWriter::stop).collect(Collectors.toList());
                stoppedWriters.forEach(w -> heartbeatWriters.remove(w.poolUuid));
            }

            // In theory we could examine the failure rate of different writers and trigger alerts on the mgmt server
            try {
                Thread.sleep(_heartBeatUpdateFreq);
            } catch (InterruptedException e) {
                s_logger.debug("[ignored] interupted between cleanups of heartbeats");
            }

//            long start = System.currentTimeMillis();
//            Set<String> storagePoolUuids = getStoragePoolUuids();
//            ScheduledExecutorService executor = Executors.newScheduledThreadPool(storagePoolUuids.size() + 1, new NamedThreadFactory("Heartbeat Writers at " + start));
//            List<Future<Boolean>> writers = storagePoolUuids.stream().map(uuid -> executor.submit(new WriteHeartbeat(uuid))).collect(Collectors.toList());
//
//            // Everyone gets 45 seconds to write their heartbeat file, then they get cancelled. Boolean is for if everyone stopped.
//            Future<Boolean> cancel = executor.schedule(() -> writers.stream()
//                    .allMatch(future -> future.isDone() || future.cancel(true)), 45, TimeUnit.SECONDS);
//
//            try {
//                boolean success = cancel.get();
//                if (!success) {
//                    s_logger.warn("Unable to cancel all heartbeat writers");
//                }
//                long remaining = start + _heartBeatUpdateFreq - System.currentTimeMillis();
//                if (remaining > 0) {
//                    Thread.sleep(remaining);
//                } else {
//                    s_logger.warn("Running behind on heartbeat by " + (-remaining) + " microseconds");
//                }
//            } catch (InterruptedException | ExecutionException e) {
//                e.printStackTrace();
//            } finally {
//                executor.shutdownNow();
//            }

//            Thread monitorThread = new Thread(new Monitor());
//            monitorThread.start();
//            try {
//                monitorThread.join();
//            } catch (InterruptedException e) {
//                s_logger.debug("[ignored] interupted joining monitor.");
//            }
//
//            try {
//                Thread.sleep(_heartBeatUpdateFreq);
//            } catch (InterruptedException e) {
//                s_logger.debug("[ignored] interupted between heartbeats.");
//            }
        }
        s_logger.info("Shutting down KVM HA Monitor");
    }
}
