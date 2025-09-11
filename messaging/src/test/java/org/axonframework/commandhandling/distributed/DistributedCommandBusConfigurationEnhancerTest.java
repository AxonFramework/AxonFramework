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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.InterceptingCommandBus;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.MessagingConfigurer;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DistributedCommandBusConfigurationEnhancer}.
 *
 * @author Jens Mayer
 */
class DistributedCommandBusConfigurationEnhancerTest {

    @Test
    void enhancesComponentRegistryWithDistributedCommandBus() {

        CommandBusConnector mockConnector = mock(CommandBusConnector.class);

        Configuration config =
                MessagingConfigurer.create()
                                   .componentRegistry(cr -> {
                                       cr.registerComponent(CommandBusConnector.class, c -> mockConnector);
                                       cr.registerEnhancer(new DistributedCommandBusConfigurationEnhancer());
                                   })
                                   .build();

        assertInstanceOf(DistributedCommandBus.class, config.getComponent(CommandBus.class));
        assertTrue(config.hasComponent(DistributedCommandBusConfiguration.class));
    }

    @Test
    void noEnhancementsWhenNoCommandBusConnectorPresent() {
        Configuration config =
                MessagingConfigurer.create()
                                   .componentRegistry(cr -> cr.registerEnhancer(
                                           new DistributedCommandBusConfigurationEnhancer()
                                   ))
                                   .build();

        CommandBus commandBus = config.getComponent(CommandBus.class);
        // Intercepting at all times, since we have a default CorrelationDataInterceptor.
        assertInstanceOf(InterceptingCommandBus.class, commandBus);
        assertFalse(config.hasComponent(DistributedCommandBusConfiguration.class));
    }
}