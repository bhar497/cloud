/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cloudstack.framework.backuprecovery.agent.api;

import com.cloud.agent.api.Command;

public class RestoreVMFromBackupCommand extends Command {

    private long vmId;
    private String vmUuid;
    private long backupId;
    private String backupUuid;

    public RestoreVMFromBackupCommand(final long vmId, final String vmUuid, final long backupId, final String backupUuid) {
        this.vmId = vmId;
        this.vmUuid = vmUuid;
        this.backupId = backupId;
        this.backupUuid = backupUuid;
    }

    public long getVmId() {
        return vmId;
    }

    public String getVmUuid() {
        return vmUuid;
    }

    public long getBackupId() {
        return backupId;
    }

    public String getBackupUuid() {
        return backupUuid;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }
}