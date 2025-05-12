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

import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
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
        ApplicationConfigurer configurer = EventSourcingConfigurer.create();
        configurer.componentRegistry(cr -> testSubject.enhance(cr));
        Configuration resultConfig = configurer.build();

        AxonServerConfiguration serverConfig = resultConfig.getComponent(AxonServerConfiguration.class);
        assertNotNull(serverConfig);
        assertEquals("localhost", serverConfig.getServers());
        assertNotNull(serverConfig.getClientId());
        assertNotNull(serverConfig.getComponentName());
        assertTrue(serverConfig.getComponentName().contains(serverConfig.getClientId()));
        assertNotNull(resultConfig.getComponent(AxonServerConnectionManager.class));
        assertInstanceOf(ManagedChannelCustomizer.class, resultConfig.getComponent(ManagedChannelCustomizer.class));
    }
}
