/*
 * Copyright (c) 2010-2025. Axon Framework
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
import org.axonframework.config.LegacyConfiguration;
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

    private final Component<EventProcessorControlService> eventProcessorControlService;

    private LegacyConfiguration config;

    /**
     * Create an default EventProcessorInfoConfiguration, which uses the {@link LegacyConfiguration} as a means to retrieve
     * the {@link EventProcessingConfiguration}, {@link AxonServerConnectionManager} and {@link
     * AxonServerConfiguration}.
     */
    public EventProcessorInfoConfiguration() {
        this(LegacyConfiguration::eventProcessingConfiguration,
             c -> c.getComponent(AxonServerConnectionManager.class),
             c -> c.getComponent(AxonServerConfiguration.class));
    }


    /**
     * Creates an EventProcessorInfoConfiguration using the provided functions to retrieve the {@link
     * EventProcessingConfiguration}, {@link AxonServerConnectionManager} and {@link AxonServerConfiguration}.
     *
     * @param eventProcessingConfigurationBuilder a Function taking in the {@link LegacyConfiguration} and providing a {@link
     *                                            EventProcessingConfiguration}
     * @param connectionManagerBuilder            a Function taking in the {@link LegacyConfiguration} and providing a {@link
     *                                            AxonServerConnectionManager}
     * @param axonServerConfigurationBuilder      a Function taking in the {@link LegacyConfiguration} and providing a {@link
     *                                            AxonServerConfiguration}
     */
    public EventProcessorInfoConfiguration(
            Function<LegacyConfiguration, EventProcessingConfiguration> eventProcessingConfigurationBuilder,
            Function<LegacyConfiguration, AxonServerConnectionManager> connectionManagerBuilder,
            Function<LegacyConfiguration, AxonServerConfiguration> axonServerConfigurationBuilder
    ) {
        this(c -> new EventProcessorControlService(
                connectionManagerBuilder.apply(c),
                eventProcessingConfigurationBuilder.apply(c),
                axonServerConfigurationBuilder.apply(c)
        ));
    }

    /**
     * Create a default EventProcessorInfoConfiguration, which uses the {@link EventProcessorControlService}
     *
     * @param eventProcessorControlService a Function taking in the {@link LegacyConfiguration} and providing a {@link
     *                                     EventProcessorControlService}
     */
    public EventProcessorInfoConfiguration(
            Function<LegacyConfiguration, EventProcessorControlService> eventProcessorControlService
    ) {
        this.eventProcessorControlService = new Component<>(
                () -> config, "eventProcessorControlService", eventProcessorControlService
        );
    }

    @Override
    public void initialize(LegacyConfiguration config) {
        this.config = config;
        // if there are no event handlers registered, there may be no EventProcessingConfiguration at all.
        if (config.eventProcessingConfiguration() != null) {
            this.config.onStart(Phase.INBOUND_EVENT_CONNECTORS, eventProcessorControlService::get);
        }
    }
}
