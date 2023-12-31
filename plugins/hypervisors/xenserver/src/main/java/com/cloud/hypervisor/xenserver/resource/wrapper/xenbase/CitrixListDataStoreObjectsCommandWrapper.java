//
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
//

package com.cloud.hypervisor.xenserver.resource.wrapper.xenbase;

import com.cloud.agent.api.Answer;
import com.cloud.hypervisor.xenserver.resource.CitrixResourceBase;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.xensource.xenapi.Types.XenAPIException;
import org.apache.cloudstack.storage.command.browser.ListDataStoreObjectsCommand;
import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;

@ResourceWrapper(handles = ListDataStoreObjectsCommand.class)
public final class CitrixListDataStoreObjectsCommandWrapper extends CommandWrapper<ListDataStoreObjectsCommand, Answer, CitrixResourceBase> {

    private static final Logger LOGGER = Logger.getLogger(CitrixListDataStoreObjectsCommandWrapper.class);

    @Override
    public Answer execute(final ListDataStoreObjectsCommand command, final CitrixResourceBase citrixResourceBase) {
        try {
            return citrixResourceBase.listFilesAtPath(command);
        } catch (XenAPIException e) {
            LOGGER.warn("XenAPI exception", e);

        } catch (XmlRpcException e) {
            LOGGER.warn("Xml Rpc Exception", e);
        } catch (Exception e) {
            LOGGER.warn("Caught exception", e);
        }
        return null;
    }
}
