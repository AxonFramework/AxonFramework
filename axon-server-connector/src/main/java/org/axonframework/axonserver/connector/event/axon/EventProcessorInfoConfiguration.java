/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import org.axonframework.config.Component;
import org.axonframework.config.Configuration;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.config.ModuleConfiguration;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.lifecycle.Phase;

import java.util.function.Function;

/**
 * Module Configuration implementation that defines the components needed to control and monitor the {@link
 * EventProcessor}s with AxonServer.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class EventProcessorInfoConfiguration implements ModuleConfiguration {

    private Configuration config;

    private final Component<EventProcessingConfiguration> eventProcessingConfiguration;
    private final Component<AxonServerConnectionManager> connectionManager;
    private final Component<AxonServerConfiguration> axonServerConfiguration;
    private final Component<EventProcessorControlService> eventProcessorControlService;

    /**
     * Create an default EventProcessorInfoConfiguration, which uses the {@link Configuration} as a means to retrieve
     * the {@link EventProcessingConfiguration}, {@link AxonServerConnectionManager} and {@link
     * AxonServerConfiguration}.
     */
    public EventProcessorInfoConfiguration() {
        this(Configuration::eventProcessingConfiguration,
             c -> c.getComponent(AxonServerConnectionManager.class),
             c -> c.getComponent(AxonServerConfiguration.class));
    }

    /**
     * Creates an EventProcessorInfoConfiguration using the provided functions to retrieve the {@link
     * EventProcessingConfiguration}, {@link AxonServerConnectionManager} and {@link AxonServerConfiguration}.
     *
     * @param eventProcessingConfiguration a Function taking in the {@link Configuration} and providing a {@link
     *                                     EventProcessingConfiguration}
     * @param connectionManager            a Function taking in the {@link Configuration} and providing a {@link
     *                                     AxonServerConnectionManager}
     * @param axonServerConfiguration      a Function taking in the {@link Configuration} and providing a {@link
     *                                     AxonServerConfiguration}
     */
    public EventProcessorInfoConfiguration(
            Function<Configuration, EventProcessingConfiguration> eventProcessingConfiguration,
            Function<Configuration, AxonServerConnectionManager> connectionManager,
            Function<Configuration, AxonServerConfiguration> axonServerConfiguration) {
        this.eventProcessingConfiguration = new Component<>(
                () -> config, "eventProcessingConfiguration", eventProcessingConfiguration
        );
        this.connectionManager = new Component<>(() -> config, "connectionManager", connectionManager);
        this.axonServerConfiguration = new Component<>(() -> config, "connectionManager", axonServerConfiguration);

        this.eventProcessorControlService = new Component<>(
                () -> config, "eventProcessorControlService",
                c -> new EventProcessorControlService(
                        this.connectionManager.get(),
                        this.eventProcessingConfiguration.get(),
                        this.axonServerConfiguration.get()
                )
        );
    }

    @Override
    public void initialize(Configuration config) {
        this.config = config;
        // if there are no event handlers registered, there is may be no EventProcessingConfiguration at all.
        if (config.eventProcessingConfiguration() != null) {
            this.config.onStart(Phase.INBOUND_EVENT_CONNECTORS, eventProcessorControlService::get);
        }
    }
}
