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

package org.axonframework.axonserver.connector;

import org.axonframework.axonserver.connector.event.AxonServerEventStorageEngine;
import org.axonframework.axonserver.connector.event.TestConverter;
import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.serialization.Converter;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link ServerConnectorConfigurationEnhancer}.
 *
 * @author Allard Buijze
 */
class ServerConnectorConfigurationEnhancerTest {

    private ServerConnectorConfigurationEnhancer testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new ServerConnectorConfigurationEnhancer();
    }

    @Test
    void orderEqualsEnhancersConstant() {
        assertEquals(ServerConnectorConfigurationEnhancer.ENHANCER_ORDER, testSubject.order());
    }

    @Test
    void enhanceSetsExpectedDefaultsInAbsenceOfTheseComponents() {
        ApplicationConfigurer configurer =
                EventSourcingConfigurer.create()
                                       .componentRegistry(registry -> registry.registerComponent(
                                               Converter.class, c -> new TestConverter()
                                       ));
        configurer.componentRegistry(cr -> testSubject.enhance(cr));
        Configuration result = configurer.build();

        AxonServerConfiguration serverConfig = result.getComponent(AxonServerConfiguration.class);
        assertNotNull(serverConfig);
        assertEquals("localhost", serverConfig.getServers());
        assertNotNull(serverConfig.getClientId());
        assertNotNull(serverConfig.getComponentName());
        assertTrue(serverConfig.getComponentName().contains(serverConfig.getClientId()));
        assertNotNull(result.getComponent(AxonServerConnectionManager.class));
        assertInstanceOf(ManagedChannelCustomizer.class, result.getComponent(ManagedChannelCustomizer.class));
        assertInstanceOf(AxonServerEventStorageEngine.class, result.getComponent(AxonServerEventStorageEngine.class));
    }
}
