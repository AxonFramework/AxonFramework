/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.axonserver.connector.event.axon;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.processor.EventProcessorControlService;
import org.axonframework.axonserver.connector.processor.EventProcessorController;
import org.axonframework.axonserver.connector.processor.grpc.GrpcEventProcessorInfoSource;
import org.axonframework.axonserver.connector.processor.schedule.ScheduledEventProcessorInfoSource;
import org.axonframework.config.Component;
import org.axonframework.config.Configuration;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.config.ModuleConfiguration;
import org.axonframework.eventhandling.EventProcessor;

import java.util.function.Function;

/**
 * Module Configuration implementation that defines the components needed to
 * control and monitor the {@link EventProcessor}s with AxonServer.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class EventProcessorInfoConfiguration implements ModuleConfiguration {

    private final Component<EventProcessingConfiguration> eventProcessingConfiguration;
    private final Component<AxonServerConnectionManager> connectionManager;

    private final Component<AxonServerConfiguration> axonServerConfiguration;

    private final Component<EventProcessorControlService> eventProcessorControlService;
    private final Component<ScheduledEventProcessorInfoSource> processorInfoSource;

    private Configuration config;

    public EventProcessorInfoConfiguration() {
        this(Configuration::eventProcessingConfiguration,
             c -> c.getComponent(AxonServerConnectionManager.class),
             c -> c.getComponent(AxonServerConfiguration.class));
    }

    public EventProcessorInfoConfiguration(
            Function<Configuration, EventProcessingConfiguration> eventProcessingConfiguration,
            Function<Configuration, AxonServerConnectionManager> connectionManager,
            Function<Configuration, AxonServerConfiguration> axonServerConfiguration) {
        this.eventProcessingConfiguration = new Component<>(() -> config, "eventProcessingConfiguration", eventProcessingConfiguration);
        this.connectionManager = new Component<>(() -> config, "connectionManager", connectionManager);
        this.axonServerConfiguration = new Component<>(() -> config, "connectionManager", axonServerConfiguration);

        this.eventProcessorControlService = new Component<>(() -> config, "eventProcessorControlService",
                                                            c -> new EventProcessorControlService(
                                                                    this.connectionManager.get(),
                                                                    new EventProcessorController(
                                                                            this.eventProcessingConfiguration.get())));
        this.processorInfoSource = new Component<>(() -> config, "eventProcessorInfoSource", c -> {
            GrpcEventProcessorInfoSource infoSource = new GrpcEventProcessorInfoSource(this.eventProcessingConfiguration.get(), this.connectionManager.get());
            return new ScheduledEventProcessorInfoSource(
                    this.axonServerConfiguration.get().getProcessorsNotificationInitialDelay(),
                    this.axonServerConfiguration.get().getProcessorsNotificationRate(),
                    infoSource);

        });
    }

    @Override
    public void initialize(Configuration config) {
        this.config = config;
    }

    @Override
    public void start() {
        processorInfoSource.get().start();
        eventProcessorControlService.get().start();
    }

    @Override
    public void shutdown() {
        processorInfoSource.get().shutdown();
    }
}
