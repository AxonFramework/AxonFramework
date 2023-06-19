/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.axonserver.connector.processor;

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.admin.AdminChannel;
import io.axoniq.axonserver.connector.control.ControlChannel;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
    private EventProcessingConfiguration processingConfiguration;
    private Map<String, AxonServerConfiguration.EventProcessorConfiguration.ProcessorSettings> processorSettings;

    private EventProcessorControlService testSubject;
    private AxonServerConnection connection;
    private AdminChannel adminChannel;

    @BeforeEach
    void setUp() {
        mockConnectionManager();
        processingConfiguration = mock(EventProcessingConfiguration.class);
        processorSettings = new HashMap<>();

        testSubject = new EventProcessorControlService(
                connectionManager, processingConfiguration, CONTEXT, processorSettings
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
    void startDoesNothingForNullAxonServerConnectionManager() {
        EventProcessorControlService unusableControlService =
                new EventProcessorControlService(null, processingConfiguration, CONTEXT, processorSettings);

        unusableControlService.start();

        verifyNoInteractions(processingConfiguration);
    }

    @Test
    void startDoesNothingForNullEventProcessingConfiguration() {
        EventProcessorControlService unusableControlService =
                new EventProcessorControlService(null, processingConfiguration, CONTEXT, processorSettings);

        unusableControlService.start();

        verifyNoInteractions(connectionManager);
    }

    @Test
    void startSetsLoadBalancingStrategiesForMatchingProcessorsThroughAdminChannel() {
        Map<String, EventProcessor> eventProcessors = new HashMap<>();
        eventProcessors.put(THIS_PROCESSOR, mock(EventProcessor.class));
        eventProcessors.put(THAT_PROCESSOR, mock(EventProcessor.class));
        when(processingConfiguration.eventProcessors()).thenReturn(eventProcessors);
        TokenStore tokenStore = mock(TokenStore.class);
        when(tokenStore.retrieveStorageIdentifier()).thenReturn(Optional.of(TOKEN_STORE_IDENTIFIER));
        when(processingConfiguration.tokenStore(anyString())).thenReturn(tokenStore);

        AxonServerConfiguration.EventProcessorConfiguration.ProcessorSettings testSetting =
                new AxonServerConfiguration.EventProcessorConfiguration.ProcessorSettings();
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
        when(processingConfiguration.eventProcessors()).thenReturn(eventProcessors);
        TokenStore tokenStore = mock(TokenStore.class);
        when(tokenStore.retrieveStorageIdentifier()).thenReturn(Optional.of(TOKEN_STORE_IDENTIFIER));
        when(processingConfiguration.tokenStore(anyString())).thenReturn(tokenStore);

        AxonServerConfiguration.EventProcessorConfiguration.ProcessorSettings testSetting =
                new AxonServerConfiguration.EventProcessorConfiguration.ProcessorSettings();
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
        when(processingConfiguration.eventProcessors()).thenReturn(eventProcessors);
        TokenStore tokenStore = mock(TokenStore.class);
        when(tokenStore.retrieveStorageIdentifier()).thenReturn(Optional.empty());
        when(processingConfiguration.tokenStore(anyString())).thenReturn(tokenStore);

        AxonServerConfiguration.EventProcessorConfiguration.ProcessorSettings testSetting =
                new AxonServerConfiguration.EventProcessorConfiguration.ProcessorSettings();
        testSetting.setLoadBalancingStrategy(LOAD_BALANCING_STRATEGY);
        processorSettings.put(THIS_PROCESSOR, testSetting);
        processorSettings.put(THAT_PROCESSOR, testSetting);

        testSubject.start();

        verify(connectionManager).getConnection(CONTEXT);
        verify(connection).adminChannel();
        verifyNoInteractions(adminChannel);
    }
}