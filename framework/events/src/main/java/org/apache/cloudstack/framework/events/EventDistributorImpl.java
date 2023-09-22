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

package org.apache.cloudstack.framework.events;

import com.cloud.utils.component.ManagerBase;
import org.apache.log4j.Logger;

import javax.annotation.PostConstruct;
import java.util.List;

public class EventDistributorImpl extends ManagerBase implements EventDistributor {
    private static final Logger LOGGER = Logger.getLogger(EventDistributorImpl.class);

    public void setEventBusses(List<EventBus> eventBusses) {
        this.eventBusses = eventBusses;
    }

    List<EventBus> eventBusses;

    @PostConstruct
    public void init() {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(String.format("testing %d event busses", eventBusses.size()));
        }
        for (EventBus bus : eventBusses) {
            try {
                bus.publish(new Event("server", "NONE","starting", "server", "NONE"));
            } catch (EventBusException e) {
                LOGGER.debug(String.format("no publish for bus %s", bus.getClass().getName()), e);
            }
        }
    }
}
