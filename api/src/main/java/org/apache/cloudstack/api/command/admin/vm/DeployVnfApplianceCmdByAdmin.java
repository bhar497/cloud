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
package org.apache.cloudstack.api.command.admin.vm;

import com.cloud.vm.VirtualMachine;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.command.admin.AdminCmd;
import org.apache.cloudstack.api.command.user.vm.DeployVnfApplianceCmd;
import org.apache.cloudstack.api.response.UserVmResponse;

@APICommand(name = "deployVnfAppliance",
        description = "Creates and automatically starts a VNF appliance based on a service offering, disk offering, and template.",
        responseObject = UserVmResponse.class,
        responseView = ResponseObject.ResponseView.Full,
        entityType = {VirtualMachine.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = true,
        since = "4.19.0")
public class DeployVnfApplianceCmdByAdmin extends DeployVnfApplianceCmd implements AdminCmd {
}
