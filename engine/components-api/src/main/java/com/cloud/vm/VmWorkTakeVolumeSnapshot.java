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
package com.cloud.vm;

import java.util.List;

import com.cloud.storage.Snapshot;

public class VmWorkTakeVolumeSnapshot extends VmWork {

    private static final long serialVersionUID = 341816293003023823L;

    private Long volumeId;
    private Long policyId;
    private Long snapshotId;
    private boolean quiesceVm;
    private Snapshot.LocationType locationType;
    private boolean asyncBackup;

    private List<Long> zoneIds;

    public VmWorkTakeVolumeSnapshot(long userId, long accountId, long vmId, String handlerName,
            Long volumeId, Long policyId, Long snapshotId, boolean quiesceVm, Snapshot.LocationType locationType,
            boolean asyncBackup, List<Long> zoneIds) {
        super(userId, accountId, vmId, handlerName);
        this.volumeId = volumeId;
        this.policyId = policyId;
        this.snapshotId = snapshotId;
        this.quiesceVm = quiesceVm;
        this.locationType = locationType;
        this.asyncBackup = asyncBackup;
        this.zoneIds = zoneIds;
    }

    public Long getVolumeId() {
        return volumeId;
    }

    public Long getPolicyId() {
        return policyId;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public boolean isQuiesceVm() {
        return quiesceVm;
    }

    public Snapshot.LocationType getLocationType() { return locationType; }

    public boolean isAsyncBackup() {
        return asyncBackup;
    }

    public List<Long> getZoneIds() {
        return zoneIds;
    }
}
