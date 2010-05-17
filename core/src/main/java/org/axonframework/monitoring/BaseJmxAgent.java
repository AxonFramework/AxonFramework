/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.monitoring;

import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.monitoring.commandhandling.CommandBusView;
import org.axonframework.monitoring.commandhandling.CommandBusViewMXBean;
import org.axonframework.monitoring.commandhandling.MonitoredCommandBus;
import org.springframework.beans.factory.annotation.Required;

import javax.annotation.PostConstruct;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

/**
 * @author Jettro Coenradie
 */
public class BaseJmxAgent {
    private MBeanServer mbeanServer;

    private SimpleEventBus eventBus;
    private MonitoredCommandBus commandBus;

    @PostConstruct
    public void init() throws Exception {
        mbeanServer = ManagementFactory.getPlatformMBeanServer();

//        SimpleEventBusManagerMXBean eventBusManager = new SimpleEventBusManager(this.eventBus);
//        ObjectName eventBusName = new ObjectName("BaseJmxAgent:name=MonitoredEventBus");
//        mbeanServer.registerMBean(eventBusManager, eventBusName);

        CommandBusViewMXBean commandBusView = new CommandBusView(this.commandBus);
        ObjectName commandBusName = new ObjectName("BaseJmxAgent:name=MonitoredCommandBus");
        mbeanServer.registerMBean(commandBusView,commandBusName);
    }

    @Required
    public void setEventBus(SimpleEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Required
    public void setCommandBus(MonitoredCommandBus commandBus) {
        this.commandBus = commandBus;
    }
}
