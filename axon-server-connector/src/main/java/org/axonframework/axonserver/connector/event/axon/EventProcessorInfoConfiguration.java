/*
 * Copyright (c) 2018. AxonIQ
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

package org.axonframework.axonserver.connector.event.axon;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.PlatformConnectionManager;
import org.axonframework.axonserver.connector.processor.EventProcessorControlService;
import org.axonframework.axonserver.connector.processor.EventProcessorController;
import org.axonframework.axonserver.connector.processor.grpc.GrpcEventProcessorInfoSource;
import org.axonframework.axonserver.connector.processor.schedule.ScheduledEventProcessorInfoSource;
import org.axonframework.config.Configuration;
import org.axonframework.config.EventHandlingConfiguration;
import org.axonframework.config.ModuleConfiguration;

/**
 * Created by Sara Pellegrini on 03/04/2018.
 * sara.pellegrini@gmail.com
 */
public class EventProcessorInfoConfiguration implements ModuleConfiguration {

    private final EventProcessorControlService eventProcessorControlService;

    private final ScheduledEventProcessorInfoSource processorInfoSource;

    public EventProcessorInfoConfiguration(
            EventHandlingConfiguration eventHandlinConf,
            PlatformConnectionManager connectionManager,
            AxonServerConfiguration configuration) {
        EventProcessorController controller = new EventProcessorController(eventHandlinConf);
        GrpcEventProcessorInfoSource infoSource = new GrpcEventProcessorInfoSource(eventHandlinConf, connectionManager);

        this.eventProcessorControlService = new EventProcessorControlService(connectionManager, controller);
        this.processorInfoSource = new ScheduledEventProcessorInfoSource(
                configuration.getProcessorsNotificationInitialDelay(),
                configuration.getProcessorsNotificationRate(),
                infoSource);
    }

    @Override
    public void initialize(Configuration config) {
    }

    @Override
    public void start() {
        processorInfoSource.start();
        eventProcessorControlService.start();
    }

    @Override
    public void shutdown() {
        processorInfoSource.shutdown();
    }
}
