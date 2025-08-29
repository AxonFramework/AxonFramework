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

import jakarta.annotation.Nonnull;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.processor.EventProcessorControlService;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.configuration.Module;
import org.axonframework.eventhandling.processors.EventProcessor;

import java.util.function.Function;

/**
 * Module Configuration implementation that defines the components needed to control and monitor the
 * {@link EventProcessor}s with AxonServer.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
// TODO #3521
public class EventProcessorInfoConfiguration implements Module {

//    private final Component<EventProcessorControlService> eventProcessorControlService;

    private Configuration config;

    /**
     * Create an default EventProcessorInfoConfiguration, which uses the {@link Configuration} as a means to retrieve
     * the {@link Configuration}, {@link AxonServerConnectionManager} and {@link AxonServerConfiguration}.
     */
    public EventProcessorInfoConfiguration() {
        this(null,
             c -> c.getComponent(AxonServerConnectionManager.class),
             c -> c.getComponent(AxonServerConfiguration.class));
    }


    /**
     * Creates an EventProcessorInfoConfiguration using the provided functions to retrieve the
     * {@code EventProcessingConfiguration}, {@link AxonServerConnectionManager} and {@link AxonServerConfiguration}.
     *
     * @param eventProcessingConfigurationBuilder a Function taking in the {@link Configuration} and providing a
     *                                            {@code EventProcessingConfiguration}
     * @param connectionManagerBuilder            a Function taking in the {@link Configuration} and providing a
     *                                            {@link AxonServerConnectionManager}
     * @param axonServerConfigurationBuilder      a Function taking in the {@link Configuration} and providing a
     *                                            {@link AxonServerConfiguration}
     */
    public EventProcessorInfoConfiguration(
            Function<Configuration, Configuration> eventProcessingConfigurationBuilder,
            Function<Configuration, AxonServerConnectionManager> connectionManagerBuilder,
            Function<Configuration, AxonServerConfiguration> axonServerConfigurationBuilder
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
     * @param eventProcessorControlService a Function taking in the {@link Configuration} and providing a
     *                                     {@link EventProcessorControlService}
     */
    public EventProcessorInfoConfiguration(
            Function<Configuration, EventProcessorControlService> eventProcessorControlService
    ) {
//        this.eventProcessorControlService =
//                ComponentDefinition.ofTypeAndName(EventProcessorControlService.class, "eventProcessorControlService");
    }

    @Override
    public String name() {
        return "";
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        this.config = config;
        // if there are no event handlers registered, there may be no EventProcessingConfiguration at all.
//        if (config.eventProcessingConfiguration() != null) {
//            lifecycleRegistry.onStart(Phase.INBOUND_EVENT_CONNECTORS, eventProcessorControlService::get);
//        }
        return null;
    }
}
