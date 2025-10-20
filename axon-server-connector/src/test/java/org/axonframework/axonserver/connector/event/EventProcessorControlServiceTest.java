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
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.processors.EventProcessor;
import org.axonframework.eventhandling.processors.streaming.token.store.TokenStore;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.Assert.*;
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

    @Test
    void startSetsLoadBalancingStrategiesForMatchingProcessorsThroughAdminChannel() {
        Map<String, EventProcessor> eventProcessors = new HashMap<>();
        eventProcessors.put(THIS_PROCESSOR, mock(EventProcessor.class));
        eventProcessors.put(THAT_PROCESSOR, mock(EventProcessor.class));
        when(processingConfiguration.getComponents(EventProcessor.class)).thenReturn(eventProcessors);

        // Mock TokenStore for THIS_PROCESSOR
        TokenStore tokenStore = mock(TokenStore.class);
        when(tokenStore.retrieveStorageIdentifier(any())).thenReturn(completedFuture(Optional.of(TOKEN_STORE_IDENTIFIER)));

        // Mock UnitOfWorkFactory and UnitOfWork
        UnitOfWork unitOfWork = mock(UnitOfWork.class);
        when(unitOfWork.executeWithResult(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, java.util.function.Function.class).apply(null);
        });
        UnitOfWorkFactory unitOfWorkFactory = mock(UnitOfWorkFactory.class);
        when(unitOfWorkFactory.create()).thenReturn(unitOfWork);

        Configuration moduleConfig = mock(Configuration.class);
        when(moduleConfig.getOptionalComponent(eq(TokenStore.class), eq("TokenStore[" + THIS_PROCESSOR + "]")))
                .thenReturn(Optional.of(tokenStore));
        when(moduleConfig.getOptionalComponent(eq(UnitOfWorkFactory.class), eq("UnitOfWorkFactory[" + THIS_PROCESSOR + "]")))
                .thenReturn(Optional.of(unitOfWorkFactory));
        when(processingConfiguration.getModuleConfiguration(THIS_PROCESSOR)).thenReturn(Optional.of(moduleConfig));

        AxonServerConfiguration.Eventhandling.ProcessorSettings testSetting =
                new AxonServerConfiguration.Eventhandling.ProcessorSettings();
        testSetting.setLoadBalancingStrategy(LOAD_BALANCING_STRATEGY);
        processorSettings.put(THIS_PROCESSOR, testSetting);
        processorSettings.put(NON_EXISTING, testSetting);

        testSubject.start();

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
    void startSetsAutomaticLoadBalancingForMatchingProcessorsThroughAdminChannel() {
        Map<String, EventProcessor> eventProcessors = new HashMap<>();
        eventProcessors.put(THIS_PROCESSOR, mock(EventProcessor.class));
        eventProcessors.put(THAT_PROCESSOR, mock(EventProcessor.class));
        when(processingConfiguration.getComponents(EventProcessor.class)).thenReturn(eventProcessors);

        // Mock TokenStore for THIS_PROCESSOR
        TokenStore tokenStore = mock(TokenStore.class);
        when(tokenStore.retrieveStorageIdentifier(any())).thenReturn(completedFuture(Optional.of(TOKEN_STORE_IDENTIFIER)));

        // Mock UnitOfWorkFactory and UnitOfWork
        UnitOfWork unitOfWork = mock(UnitOfWork.class);
        when(unitOfWork.executeWithResult(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, java.util.function.Function.class).apply(null);
        });
        UnitOfWorkFactory unitOfWorkFactory = mock(UnitOfWorkFactory.class);
        when(unitOfWorkFactory.create()).thenReturn(unitOfWork);

        Configuration moduleConfig = mock(Configuration.class);
        when(moduleConfig.getOptionalComponent(eq(TokenStore.class), eq("TokenStore[" + THIS_PROCESSOR + "]")))
                .thenReturn(Optional.of(tokenStore));
        when(moduleConfig.getOptionalComponent(eq(UnitOfWorkFactory.class), eq("UnitOfWorkFactory[" + THIS_PROCESSOR + "]")))
                .thenReturn(Optional.of(unitOfWorkFactory));
        when(processingConfiguration.getModuleConfiguration(THIS_PROCESSOR)).thenReturn(Optional.of(moduleConfig));

        AxonServerConfiguration.Eventhandling.ProcessorSettings testSetting =
                new AxonServerConfiguration.Eventhandling.ProcessorSettings();
        testSetting.setLoadBalancingStrategy(LOAD_BALANCING_STRATEGY);
        testSetting.setAutomaticBalancing(true);
        processorSettings.put(THIS_PROCESSOR, testSetting);
        processorSettings.put(NON_EXISTING, testSetting);

        testSubject.start();

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
    void startDoesNotSetLoadBalancingStrategiesWhenTheTokenStoreCannotBeFound() {
        Map<String, EventProcessor> eventProcessors = new HashMap<>();
        eventProcessors.put(THIS_PROCESSOR, mock(EventProcessor.class));
        eventProcessors.put(THAT_PROCESSOR, mock(EventProcessor.class));
        when(processingConfiguration.getComponents(EventProcessor.class)).thenReturn(eventProcessors);

        // Mock empty module configuration (no TokenStore found)
        when(processingConfiguration.getModuleConfiguration(THIS_PROCESSOR)).thenReturn(Optional.empty());
        when(processingConfiguration.getModuleConfiguration(THAT_PROCESSOR)).thenReturn(Optional.empty());

        AxonServerConfiguration.Eventhandling.ProcessorSettings testSetting =
                new AxonServerConfiguration.Eventhandling.ProcessorSettings();
        testSetting.setLoadBalancingStrategy(LOAD_BALANCING_STRATEGY);
        processorSettings.put(THIS_PROCESSOR, testSetting);
        processorSettings.put(THAT_PROCESSOR, testSetting);

        testSubject.start();

        verify(connectionManager).getConnection(CONTEXT);
        verify(connection).adminChannel();
        verifyNoInteractions(adminChannel);
    }
}