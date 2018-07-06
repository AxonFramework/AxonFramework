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

package io.axoniq.axonhub.client.event.axon;

import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.axonhub.client.processor.EventProcessorControlService;
import io.axoniq.axonhub.client.processor.EventProcessorController;
import io.axoniq.axonhub.client.processor.grpc.GrpcEventProcessorInfoSource;
import io.axoniq.axonhub.client.processor.schedule.ScheduledEventProcessorInfoSource;
import org.axonframework.config.Configuration;
import org.axonframework.config.EventHandlingConfiguration;
import org.axonframework.config.ModuleConfiguration;

/**
 * Created by Sara Pellegrini on 03/04/2018.
 * sara.pellegrini@gmail.com
 */
public class AxonHubEventProcessorInfoConfiguration implements ModuleConfiguration {

    private final EventProcessorControlService eventProcessorControlService;

    private final ScheduledEventProcessorInfoSource processorInfoSource;

    public AxonHubEventProcessorInfoConfiguration(
            EventHandlingConfiguration eventHandlingConfiguration,
            PlatformConnectionManager connectionManager,
            AxonHubConfiguration configuration) {
        EventProcessorController processorController = new EventProcessorController(eventHandlingConfiguration);
        this.eventProcessorControlService = new EventProcessorControlService(connectionManager, processorController);

        GrpcEventProcessorInfoSource delegate = new GrpcEventProcessorInfoSource(
                eventHandlingConfiguration,
                connectionManager);
        this.processorInfoSource = new ScheduledEventProcessorInfoSource(
                configuration.getProcessorsNotificationInitialDelay(),
                configuration.getProcessorsNotificationRate(),
                delegate);
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
