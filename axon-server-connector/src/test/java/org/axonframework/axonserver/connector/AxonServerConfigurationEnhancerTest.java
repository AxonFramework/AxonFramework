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
import org.axonframework.messaging.commandhandling.distributed.CommandBusConnector;
import org.axonframework.messaging.commandhandling.distributed.PayloadConvertingCommandBusConnector;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.messaging.queryhandling.distributed.PayloadConvertingQueryBusConnector;
import org.axonframework.messaging.queryhandling.distributed.QueryBusConnector;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AxonServerConfigurationEnhancer}.
 *
 * @author Allard Buijze
 */
class AxonServerConfigurationEnhancerTest {

    private AxonServerConfigurationEnhancer testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new AxonServerConfigurationEnhancer();
    }

    @Test
    void orderEqualsEnhancersConstant() {
        assertEquals(AxonServerConfigurationEnhancer.ENHANCER_ORDER, testSubject.order());
    }

    @Test
    void enhanceSetsExpectedDefaultsInAbsenceOfTheseComponents() {
        Configuration result = EventSourcingConfigurer.create()
                                                      .componentRegistry(ComponentRegistry::disableEnhancerScanning)
                                                      .componentRegistry(cr -> testSubject.enhance(cr))
                                                      .build();

        AxonServerConfiguration serverConfig = result.getComponent(AxonServerConfiguration.class);
        assertNotNull(serverConfig);
        assertEquals("localhost", serverConfig.getServers());
        assertNotNull(serverConfig.getClientId());
        assertNotNull(serverConfig.getComponentName());
        assertTrue(serverConfig.getComponentName().contains(serverConfig.getClientId()));
        assertNotNull(result.getComponent(AxonServerConnectionManager.class));
        assertInstanceOf(ManagedChannelCustomizer.class, result.getComponent(ManagedChannelCustomizer.class));
        assertInstanceOf(AxonServerEventStorageEngine.class, result.getComponent(EventStorageEngine.class));
        assertInstanceOf(PayloadConvertingCommandBusConnector.class, result.getComponent(CommandBusConnector.class));
        assertInstanceOf(PayloadConvertingQueryBusConnector.class, result.getComponent(QueryBusConnector.class));
    }

    @Test
    void noRegisteredTopologyChangeListenerInvokesConnectionManagerGetConnectionOnce() {
        AxonConfiguration result =
                EventSourcingConfigurer.create()
                                       .componentRegistry(ComponentRegistry::disableEnhancerScanning)
                                       .componentRegistry(cr -> testSubject.enhance(cr))
                                       .lifecycleRegistry(registry -> registry.registerLifecyclePhaseTimeout(
                                               25, TimeUnit.MILLISECONDS
                                       ))
                                       .componentRegistry(registry -> registry.registerDecorator(
                                               AxonServerConnectionManager.class,
                                               0,
                                               (config, name, delegate) -> Mockito.spy(delegate)
                                       ))
                                       .build();

        result.start();

        AxonServerConnectionManager connectionManager = result.getComponent(AxonServerConnectionManager.class);
        assertNotNull(connectionManager);
        await().pollDelay(Duration.ofMillis(50))
               .atMost(Duration.ofMillis(500))
               .untilAsserted(() -> verify(connectionManager, atLeastOnce()).getConnection());
    }

    /**
     * This test would ideally verify a {@link TopologyChangeListener} got registered with the
     * {@link io.axoniq.axonserver.connector.control.ControlChannel} of the default context of the
     * {@link AxonServerConnectionManager}. However, this test class is not set up to spin an actual Axon Server
     * instance. Hence, I have decided to only verify if the {@link AxonServerConnectionManager#getConnection()} was
     * invoked twice, where the first occurrence comes from the
     * {@link org.axonframework.axonserver.connector.command.AxonServerCommandBusConnector} and the second from
     * registering the {@code TopologyChangeListener}.
     */
    @Test
    void registeredTopologyChangeListenerInvokesConnectionManagerGetConnectionTwice() {
        AxonConfiguration result =
                EventSourcingConfigurer.create()
                                       .componentRegistry(ComponentRegistry::disableEnhancerScanning)
                                       .componentRegistry(cr -> testSubject.enhance(cr))
                                       .lifecycleRegistry(registry -> registry.registerLifecyclePhaseTimeout(
                                               25, TimeUnit.MILLISECONDS
                                       ))
                                       .componentRegistry(registry -> registry.registerDecorator(
                                               AxonServerConnectionManager.class,
                                               Integer.MIN_VALUE,
                                               (config, name, delegate) -> Mockito.spy(delegate)
                                       ))
                                       .componentRegistry(registry -> registry.registerComponent(
                                               TopologyChangeListener.class,
                                               c -> Mockito.mock(TopologyChangeListener.class)
                                       ))
                                       .build();

        result.start();

        AxonServerConnectionManager connectionManager = result.getComponent(AxonServerConnectionManager.class);
        assertNotNull(connectionManager);
        await().pollDelay(Duration.ofMillis(50))
               .atMost(Duration.ofMillis(500))
               .untilAsserted(() -> verify(connectionManager, atLeastOnce()).getConnection());
    }

    @Nested
    class EventProcessorControlServiceTests {

        @Test
        void eventProcessorControlServiceIsRegistered() {
            // given / when
            Configuration result = EventSourcingConfigurer.create()
                                                          .componentRegistry(ComponentRegistry::disableEnhancerScanning)
                                                          .componentRegistry(cr -> testSubject.enhance(cr))
                                                          .build();

            // then
            assertNotNull(result.getComponent(
                    org.axonframework.axonserver.connector.event.EventProcessorControlService.class
            ));
        }

        @Test
        void eventProcessorControlServiceStartsAndInvokesConnectionManagerGetConnection() {
            // given
            AxonConfiguration result =
                    EventSourcingConfigurer.create()
                                           .componentRegistry(ComponentRegistry::disableEnhancerScanning)
                                           .componentRegistry(cr -> testSubject.enhance(cr))
                                           .lifecycleRegistry(registry -> registry.registerLifecyclePhaseTimeout(
                                                   25, TimeUnit.MILLISECONDS
                                           ))
                                           .componentRegistry(registry -> registry.registerDecorator(
                                                   AxonServerConnectionManager.class,
                                                   Integer.MIN_VALUE,
                                                   (config, name, delegate) -> Mockito.spy(delegate)
                                           ))
                                           .build();

            // when
            result.start();

            // then
            AxonServerConnectionManager connectionManager = result.getComponent(AxonServerConnectionManager.class);
            assertNotNull(connectionManager);
            await().pollDelay(Duration.ofMillis(50))
                   .atMost(Duration.ofMillis(500))
                   .untilAsserted(() -> verify(connectionManager, atLeastOnce()).getConnection());
        }
    }
}