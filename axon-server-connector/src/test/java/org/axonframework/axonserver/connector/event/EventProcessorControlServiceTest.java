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

package org.axonframework.axonserver.connector.event;

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.admin.AdminChannel;
import io.axoniq.axonserver.connector.control.ControlChannel;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.processing.EventProcessor;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.core.unitofwork.UnitOfWork;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link EventProcessorControlService}.
 *
 * @author Steven van Beelen
 */
class EventProcessorControlServiceTest {

    private static final String CONTEXT = "some-context";
    private static final String THIS_PROCESSOR = "this-processor";
    private static final String THAT_PROCESSOR = "that-processor";
    private static final String NON_EXISTING = "non-existing";
    private static final String TOKEN_STORE_IDENTIFIER = "some-identifier";
    private static final String LOAD_BALANCING_STRATEGY = "some-strategy";

    private AxonServerConnectionManager connectionManager;
    private Configuration processingConfiguration;
    private Map<String, AxonServerConfiguration.Eventhandling.ProcessorSettings> processorSettings;

    private EventProcessorControlService testSubject;
    private AxonServerConnection connection;
    private AdminChannel adminChannel;

    @BeforeEach
    void setUp() {
        mockConnectionManager();
        processingConfiguration = mock(Configuration.class);
        processorSettings = new HashMap<>();

        testSubject = new EventProcessorControlService(
                processingConfiguration, connectionManager, CONTEXT, processorSettings
        );
    }

    private void mockConnectionManager() {
        connectionManager = mock(AxonServerConnectionManager.class);
        connection = mock(AxonServerConnection.class);
        when(connectionManager.getConnection(CONTEXT)).thenReturn(connection);

        ControlChannel controlChannel = mock(ControlChannel.class);
        when(connection.controlChannel()).thenReturn(controlChannel);

        adminChannel = mock(AdminChannel.class);
        CompletableFuture<Void> loadBalancingResult = new CompletableFuture<>();
        loadBalancingResult.complete(null);
        when(adminChannel.loadBalanceEventProcessor(anyString(), anyString(), anyString()))
                .thenReturn(loadBalancingResult);
        when(adminChannel.setAutoLoadBalanceStrategy(anyString(), anyString(), anyString()))
                .thenReturn(loadBalancingResult);
        when(connection.adminChannel()).thenReturn(adminChannel);
    }

    @Test
    void cannotConstructForNullAxonServerConnectionManager() {
        assertThrows(
                NullPointerException.class,
                () -> new EventProcessorControlService(processingConfiguration, null, CONTEXT, processorSettings)
        );
    }

    @Test
    void cannotConstructForNullConfiguration() {
        assertThrows(
                NullPointerException.class,
                () -> new EventProcessorControlService(null, connectionManager, CONTEXT, processorSettings)
        );
    }

    @Nested
    class StartTests {

        @Test
        void setsLoadBalancingStrategiesForMatchingProcessorsThroughAdminChannel() {
            // given
            setupEventProcessors();
            setupModuleConfigurationFor(THIS_PROCESSOR);
            setupProcessorSettings(THIS_PROCESSOR, false);
            setupProcessorSettings(NON_EXISTING, false);

            // when
            testSubject.start();

            // then
            verify(connectionManager).getConnection(CONTEXT);
            verify(connection).adminChannel();
            verify(adminChannel).loadBalanceEventProcessor(
                    THIS_PROCESSOR,
                    TOKEN_STORE_IDENTIFIER,
                    LOAD_BALANCING_STRATEGY
            );
            // There is no registered strategy for THAT_PROCESSOR.
            verify(adminChannel, never()).loadBalanceEventProcessor(eq(THAT_PROCESSOR), anyString(), anyString());
            // There is no Event Processor called NON_EXISTING.
            verify(adminChannel, never()).loadBalanceEventProcessor(eq(NON_EXISTING), anyString(), anyString());
            // Nobody turned on automatic balancing
            verify(adminChannel, never()).setAutoLoadBalanceStrategy(anyString(), anyString(), anyString());
        }

        @Test
        void setsAutomaticLoadBalancingForMatchingProcessorsThroughAdminChannel() {
            // given
            setupEventProcessors();
            setupModuleConfigurationFor(THIS_PROCESSOR);
            setupProcessorSettings(THIS_PROCESSOR, true);
            setupProcessorSettings(NON_EXISTING, true);

            // when
            testSubject.start();

            // then
            verify(connectionManager).getConnection(CONTEXT);
            verify(connection).adminChannel();
            verify(adminChannel).loadBalanceEventProcessor(
                    THIS_PROCESSOR,
                    TOKEN_STORE_IDENTIFIER,
                    LOAD_BALANCING_STRATEGY
            );
            verify(adminChannel).setAutoLoadBalanceStrategy(
                    THIS_PROCESSOR,
                    TOKEN_STORE_IDENTIFIER,
                    LOAD_BALANCING_STRATEGY
            );

            // There is no registered strategy for THAT_PROCESSOR.
            verify(adminChannel, never()).loadBalanceEventProcessor(eq(THAT_PROCESSOR), anyString(), anyString());
            verify(adminChannel, never()).setAutoLoadBalanceStrategy(eq(THAT_PROCESSOR), anyString(), anyString());
            // There is no Event Processor called NON_EXISTING.
            verify(adminChannel, never()).loadBalanceEventProcessor(eq(NON_EXISTING), anyString(), anyString());
            verify(adminChannel, never()).setAutoLoadBalanceStrategy(eq(NON_EXISTING), anyString(), anyString());
        }

        @Test
        void doesNotSetLoadBalancingStrategiesWhenTheTokenStoreCannotBeFound() {
            // given
            setupEventProcessors();
            when(processingConfiguration.getModuleConfiguration(THIS_PROCESSOR)).thenReturn(Optional.empty());
            when(processingConfiguration.getModuleConfiguration(THAT_PROCESSOR)).thenReturn(Optional.empty());
            setupProcessorSettings(THIS_PROCESSOR, false);
            setupProcessorSettings(THAT_PROCESSOR, false);

            // when
            testSubject.start();

            // then
            verify(connectionManager).getConnection(CONTEXT);
            verify(connection).adminChannel();
            verifyNoInteractions(adminChannel);
        }
    }

    private void setupEventProcessors() {
        Map<String, EventProcessor> eventProcessors = new HashMap<>();
        eventProcessors.put(THIS_PROCESSOR, mock(EventProcessor.class));
        eventProcessors.put(THAT_PROCESSOR, mock(EventProcessor.class));
        when(processingConfiguration.getComponents(EventProcessor.class)).thenReturn(eventProcessors);
    }

    private void setupModuleConfigurationFor(String processorName) {
        TokenStore tokenStore = mock(TokenStore.class);
        when(tokenStore.retrieveStorageIdentifier(any()))
                .thenReturn(completedFuture(TOKEN_STORE_IDENTIFIER));

        UnitOfWork unitOfWork = mock(UnitOfWork.class);
        when(unitOfWork.executeWithResult(any()))
                .thenAnswer(invocation -> invocation.<java.util.function.Function<?, ?>>getArgument(0).apply(null));
        UnitOfWorkFactory unitOfWorkFactory = mock(UnitOfWorkFactory.class);
        when(unitOfWorkFactory.create()).thenReturn(unitOfWork);

        Configuration moduleConfig = mock(Configuration.class);
        when(moduleConfig.getOptionalComponent(eq(TokenStore.class), eq("TokenStore[" + processorName + "]")))
                .thenReturn(Optional.of(tokenStore));
        when(moduleConfig.getOptionalComponent(eq(UnitOfWorkFactory.class), eq("UnitOfWorkFactory[" + processorName + "]")))
                .thenReturn(Optional.of(unitOfWorkFactory));
        when(processingConfiguration.getModuleConfiguration(processorName)).thenReturn(Optional.of(moduleConfig));
    }

    private void setupProcessorSettings(String processorName, boolean automaticBalancing) {
        AxonServerConfiguration.Eventhandling.ProcessorSettings settings =
                new AxonServerConfiguration.Eventhandling.ProcessorSettings();
        settings.setLoadBalancingStrategy(LOAD_BALANCING_STRATEGY);
        settings.setAutomaticBalancing(automaticBalancing);
        processorSettings.put(processorName, settings);
    }
}