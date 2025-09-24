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

package org.axonframework.config;

import jakarta.annotation.Nonnull;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.configuration.SubscribableMessageSourceDefinition;
import org.axonframework.eventhandling.annotations.AnnotationEventHandlerAdapter;
import org.axonframework.eventhandling.processors.errorhandling.ErrorContext;
import org.axonframework.eventhandling.processors.errorhandling.ErrorHandler;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.processors.EventProcessor;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.processors.errorhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.MultiEventHandlerInvoker;
import org.axonframework.eventhandling.processors.errorhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.processors.subscribing.SubscribingEventProcessor;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventhandling.annotations.EventHandler;
import org.axonframework.eventhandling.sequencing.FullConcurrencyPolicy;
import org.axonframework.eventhandling.sequencing.SequentialPolicy;
import org.axonframework.eventhandling.deadletter.DeadLetteringEventHandlerInvoker;
import org.axonframework.eventhandling.processors.streaming.pooled.PooledStreamingEventProcessor;
import org.axonframework.eventhandling.processors.streaming.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.eventhandling.processors.streaming.token.store.TokenStore;
import org.axonframework.eventhandling.processors.streaming.token.store.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.eventstore.LegacyEmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.LegacyEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.LegacyInMemoryEventStorageEngine;
import org.axonframework.eventstreaming.TrackingTokenSource;
import org.axonframework.lifecycle.LifecycleHandlerInvocationException;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.axonframework.common.ReflectionUtils.ensureAccessible;
import static org.axonframework.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link EventProcessingModule}.
 *
 * @author Allard Buijze
 */
@ExtendWith(MockitoExtension.class)
class EventProcessingModuleTest {

    private LegacyEventStore eventStoreOne;
    private LegacyEventStore eventStoreTwo;

    private LegacyConfigurer configurer;

    @BeforeEach
    void setUp() {
        configurer = LegacyDefaultConfigurer.defaultConfiguration();

        eventStoreOne = spy(LegacyEmbeddedEventStore.builder()
                                                    .storageEngine(new LegacyInMemoryEventStorageEngine())
                                                    .build());
        eventStoreTwo = spy(LegacyEmbeddedEventStore.builder()
                                                    .storageEngine(new LegacyInMemoryEventStorageEngine())
                                                    .build());

        eventStoreOne.publish(EventTestUtils.asEventMessage("test1"));
        eventStoreTwo.publish(EventTestUtils.asEventMessage("test2"));
    }

    @Disabled("#3710 Reenable after ParameterResolver fix")
    @Test
    void processorsDefaultToSubscribingWhenUsingSimpleEventBus() {
        LegacyConfiguration configuration =
                LegacyDefaultConfigurer.defaultConfiguration()
                                       .configureEventBus(c -> SimpleEventBus.builder().build())
                                       .eventProcessing(ep -> ep.registerEventHandler(c -> new SubscribingEventHandler())
                                                                .registerEventHandler(c -> new TrackingEventHandler()))
                                       .start();

        EventProcessingConfiguration processingConfig = configuration.eventProcessingConfiguration();

        assertTrue(processingConfig.eventProcessor("subscribing").isPresent());
        assertTrue(processingConfig.eventProcessor("subscribing")
                                   .map(p -> p instanceof SubscribingEventProcessor)
                                   .orElse(false));
        assertTrue(processingConfig.eventProcessor("tracking").isPresent());
        assertTrue(processingConfig.eventProcessor("tracking")
                                   .map(p -> p instanceof SubscribingEventProcessor)
                                   .orElse(false));
    }

    @Disabled("#3710 Reenable after ParameterResolver fix")
    @Test
    void createSubscribingEventProcessorIfSubscribableMessageSourceDefinitionBuilderPresent(
            @Mock EventProcessingConfigurer.SubscribableMessageSourceDefinitionBuilder mockBuilder,
            @Mock SubscribableMessageSourceDefinition<EventMessage> definition,
            @Mock SubscribableMessageSource source) {
        when(mockBuilder.build("pooled-streaming")).thenReturn(definition);
        when(mockBuilder.build("tracking")).thenReturn(definition);
        when(definition.create(any())).thenReturn(source);

        configurer.eventProcessing()
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerEventHandler(config -> new TrackingEventHandler())
                  .usingSubscribingEventProcessors(mockBuilder);
        LegacyConfiguration config = configurer.start();

        Map<String, EventProcessor> processorMap = config.eventProcessingConfiguration().eventProcessors();

        processorMap.forEach((c, processor) -> assertInstanceOf(SubscribingEventProcessor.class, processor));
        assertEquals(2, processorMap.size());
        verify(mockBuilder, times(2)).build(anyString());
    }

    @Disabled("#3710 Reenable after ParameterResolver fix")
    @Test
    void subscribingProcessorsUsesSpecificSource() {
        configurer.eventProcessing()
                  .configureDefaultSubscribableMessageSource(c -> eventStoreOne)
                  .registerSubscribingEventProcessor("subscribing", c -> eventStoreTwo)
                  .registerEventHandler(c -> new SubscribingEventHandler());

        LegacyConfiguration config = configurer.start();
        Optional<SubscribingEventProcessor> processor = config.eventProcessingConfiguration()
                                                              .eventProcessor("subscribing");
        assertTrue(processor.isPresent());
        assertEquals(eventStoreTwo, processor.get().getMessageSource());
    }

    @Test
    void packageOfObject() {
        String expectedPackageName = EventProcessingModule.class.getPackage().getName();
        assertEquals(expectedPackageName, EventProcessingModule.packageOfObject(this));
    }

    @Test
    void sagaPooledStreamingProcessorConstructionUsesDefaultSagaProcessorConfigIfNoCustomizationIsPresent()
            throws NoSuchFieldException, IllegalAccessException {
        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> eventStoreOne)
                  .registerSaga(Object.class);
        LegacyConfiguration config = configurer.start();

        Optional<PooledStreamingEventProcessor> resultPsep =
                config.eventProcessingConfiguration()
                      .eventProcessor("ObjectProcessor", PooledStreamingEventProcessor.class);
        assertTrue(resultPsep.isPresent());

        PooledStreamingEventProcessor psep = resultPsep.get();
        PooledStreamingEventProcessorConfiguration configuration = getField("configuration", psep);
        long tokenClaimInterval =
                getField("tokenClaimInterval", configuration);
        assertEquals(5000L, tokenClaimInterval);

        Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialToken =
                getField("initialToken", configuration);
        initialToken.apply(eventStoreTwo);
        verify(eventStoreTwo, times(0)).createTailToken();
        // The default Saga Config starts the stream at the head
        verify(eventStoreTwo).createHeadToken();
    }

    @Test
    void sagaPooledStreamingProcessorConstructionDoesNotPickDefaultSagaProcessorConfigForCustomProcessor()
            throws NoSuchFieldException, IllegalAccessException {
        configurer.eventProcessing()
                  .assignProcessingGroup(someGroup -> "custom-processor")
                  .registerPooledStreamingEventProcessor("custom-processor", config -> eventStoreOne)
                  .registerSaga(CustomSaga.class);
        LegacyConfiguration config = configurer.start();

        Optional<PooledStreamingEventProcessor> resultPsep =
                config.eventProcessingConfiguration()
                      .eventProcessor("custom-processor", PooledStreamingEventProcessor.class);
        assertTrue(resultPsep.isPresent());

        PooledStreamingEventProcessor psep = resultPsep.get();
        PooledStreamingEventProcessorConfiguration configuration = getField("configuration", psep);
        long tokenClaimInterval =
                getField("tokenClaimInterval", configuration);
        assertEquals(5000L, tokenClaimInterval);

        Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialToken =
                getField("initialToken", configuration);
        TrackingToken actualInitialToken = initialToken.apply(eventStoreTwo).join();
        // In absence of the default Saga Config, the stream starts at the tail
        assertEquals(0, actualInitialToken.position().orElse(-1));
        // to create the default replay token, we need to retrieve the head token
        verify(eventStoreTwo).createHeadToken();
    }

    @Test
    void sagaPooledStreamingProcessorConstructionDoesNotPickDefaultSagaProcessorConfigForCustomPooledStreamingProcessorBuilder()
            throws NoSuchFieldException, IllegalAccessException {
        EventProcessingConfigurer.PooledStreamingProcessorConfiguration testPsepConfig =
                (config, builder) -> builder.maxClaimedSegments(4);
        configurer.eventProcessing()
                  .registerPooledStreamingEventProcessor("ObjectProcessor", config -> eventStoreOne, testPsepConfig)
                  .registerSaga(Object.class);
        LegacyConfiguration config = configurer.start();

        Optional<PooledStreamingEventProcessor> resultPsep =
                config.eventProcessingConfiguration()
                      .eventProcessor("ObjectProcessor", PooledStreamingEventProcessor.class);
        assertTrue(resultPsep.isPresent());

        PooledStreamingEventProcessor psep = resultPsep.get();
        PooledStreamingEventProcessorConfiguration configuration = getField("configuration", psep);
        Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialToken =
                getField("initialToken", configuration);
        TrackingToken actualInitialToken = initialToken.apply(eventStoreTwo).join();
        // In absence of the default Saga Config, the stream starts at the tail
        assertEquals(0, actualInitialToken.position().orElse(-1));
        // to create the default replay token, we need to retrieve the head token
        verify(eventStoreTwo).createHeadToken();
    }

    @Test
    void sagaPooledStreamingProcessorConstructionDoesNotPickDefaultSagaProcessorConfigForCustomConfigInstance()
            throws NoSuchFieldException, IllegalAccessException {
        EventProcessingConfigurer.PooledStreamingProcessorConfiguration testPsepConfig =
                (config, builder) -> builder.maxClaimedSegments(4);
        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> eventStoreOne)
                  .registerSaga(Object.class)
                  .registerPooledStreamingEventProcessorConfiguration("ObjectProcessor", testPsepConfig);
        LegacyConfiguration config = configurer.start();

        Optional<PooledStreamingEventProcessor> resultPsep =
                config.eventProcessingConfiguration()
                      .eventProcessor("ObjectProcessor", PooledStreamingEventProcessor.class);
        assertTrue(resultPsep.isPresent());

        PooledStreamingEventProcessor psep = resultPsep.get();
        PooledStreamingEventProcessorConfiguration configuration = getField("configuration", psep);
        Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialToken =
                getField("initialToken", configuration);

        TrackingToken actualInitialToken = initialToken.apply(eventStoreTwo).join();
        // In absence of the default Saga Config, the stream starts at the tail
        assertEquals(0, actualInitialToken.position().orElse(-1));
        // to create the default replay token, we need to retrieve the head token
        verify(eventStoreTwo).createHeadToken();
    }

    @Test
    void sagaPooledStreamingProcessorConstructionDoesNotPickDefaultSagaProcessorConfigForCustomDefaultConfig()
            throws NoSuchFieldException, IllegalAccessException {
        EventProcessingConfigurer.PooledStreamingProcessorConfiguration psepConfig =
                (config, builder) -> builder.maxClaimedSegments(4);
        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors(psepConfig)
                  .configureDefaultStreamableMessageSource(config -> eventStoreOne)
                  .registerSaga(Object.class);
        LegacyConfiguration config = configurer.start();

        Optional<PooledStreamingEventProcessor> resultPsep =
                config.eventProcessingConfiguration()
                      .eventProcessor("ObjectProcessor", PooledStreamingEventProcessor.class);
        assertTrue(resultPsep.isPresent());

        PooledStreamingEventProcessor psep = resultPsep.get();
        PooledStreamingEventProcessorConfiguration configuration = getField("configuration", psep);
        Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialToken =
                getField("initialToken", configuration);
        TrackingToken actualInitialToken = initialToken.apply(eventStoreTwo).join();
        // In absence of the default Saga Config, the stream starts at the tail
        assertEquals(0, actualInitialToken.position().orElse(-1));
        // to create the default replay token, we need to retrieve the head token
        verify(eventStoreTwo).createHeadToken();
    }

    @Test
    void configurePooledStreamingEventProcessorFailsInAbsenceOfStreamableMessageSource() {
        String testName = "pooled-streaming";
        // This configurer does not contain an EventStore or other StreamableMessageSource.
        configurer.eventProcessing()
                  .registerPooledStreamingEventProcessor(testName)
                  .registerEventHandler(config -> new PooledStreamingEventHandler());
        assertThrows(LifecycleHandlerInvocationException.class, () -> configurer.start());
    }

    @Test
    void usingPooledStreamingEventProcessorWithConfigurationIsUsedDuringAllPsepConstructions()
            throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        int testCapacity = 24;
        Object testHandler = new Object();

        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors((config, builder) -> builder.maxClaimedSegments(testCapacity))
                  .configureDefaultStreamableMessageSource(config -> eventStoreOne)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .byDefaultAssignTo("default")
                  .registerEventHandler(config -> testHandler);
        LegacyConfiguration config = configurer.start();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertTrue(optionalResult.isPresent());
        PooledStreamingEventProcessor result = optionalResult.get();
        assertEquals(testCapacity, result.maxCapacity());
//        assertEquals(eventStoreOne, getField("eventSource", result)); fixme: temporarily LegacyStreamableEventSource is used

        optionalResult = config.eventProcessingConfiguration()
                               .eventProcessor("default", PooledStreamingEventProcessor.class);

        assertTrue(optionalResult.isPresent());
        result = optionalResult.get();
        assertEquals(testCapacity, result.maxCapacity());
//        assertEquals(eventStoreOne, getField("eventSource", result)); fixme: temporarily LegacyStreamableEventSource is used
    }

    @Disabled("TODO #3517 - Revise Dead Letter Queue")
    @Test
    void registerDeadLetterQueueConstructsDeadLetteringEventHandlerInvoker(
            @Mock SequencedDeadLetterQueue<EventMessage> deadLetterQueue
    ) throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        String processingGroup = "pooled-streaming";

        configurer.configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> deadLetterQueue);
        LegacyConfiguration config = configurer.start();

        Optional<EnqueuePolicy<EventMessage>> optionalPolicy = config.eventProcessingConfiguration()
                                                                        .deadLetterPolicy(processingGroup);
        assertTrue(optionalPolicy.isPresent());
        EnqueuePolicy<EventMessage> expectedPolicy = optionalPolicy.get();

        Optional<SequencedDeadLetterQueue<EventMessage>> configuredDlq =
                config.eventProcessingConfiguration().deadLetterQueue(processingGroup);
        assertTrue(configuredDlq.isPresent());
        assertEquals(deadLetterQueue, configuredDlq.get());

        Optional<PooledStreamingEventProcessor> optionalProcessor =
                config.eventProcessingConfiguration()
                      .eventProcessor(processingGroup, PooledStreamingEventProcessor.class);
        assertTrue(optionalProcessor.isPresent());
        PooledStreamingEventProcessor resultProcessor = optionalProcessor.get();

        EventHandlerInvoker resultInvoker = (EventHandlerInvoker) ensureAccessible(
                resultProcessor.getClass().getDeclaredMethod("eventHandlerInvoker")
        ).invoke(resultProcessor);
        assertEquals(MultiEventHandlerInvoker.class, resultInvoker.getClass());

        MultiEventHandlerInvoker resultMultiInvoker = ((MultiEventHandlerInvoker) resultInvoker);
        List<EventHandlerInvoker> delegates = getField("delegates", resultMultiInvoker);
        assertFalse(delegates.isEmpty());
        DeadLetteringEventHandlerInvoker resultDeadLetteringInvoker =
                ((DeadLetteringEventHandlerInvoker) delegates.getFirst());

        assertEquals(deadLetterQueue, getField("queue", resultDeadLetteringInvoker));
        assertEquals(expectedPolicy, getField("enqueuePolicy", resultDeadLetteringInvoker));
        assertEquals(NoTransactionManager.INSTANCE, getField("transactionManager", resultDeadLetteringInvoker));
    }

    @Disabled("TODO #3517 - Revise Dead Letter Queue")
    @Test
    void registerDefaultDeadLetterPolicyIsUsed(@Mock SequencedDeadLetterQueue<EventMessage> deadLetterQueue)
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        String processingGroup = "pooled-streaming";
        EnqueuePolicy<EventMessage> expectedPolicy = (letter, cause) -> Decisions.ignore();

        configurer.configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> deadLetterQueue)
                  .registerDefaultDeadLetterPolicy(c -> expectedPolicy);
        LegacyConfiguration config = configurer.start();

        Optional<EnqueuePolicy<EventMessage>> optionalPolicy = config.eventProcessingConfiguration()
                                                                        .deadLetterPolicy(processingGroup);
        assertTrue(optionalPolicy.isPresent());
        EnqueuePolicy<EventMessage> resultPolicy = optionalPolicy.get();
        assertEquals(expectedPolicy, resultPolicy);

        Optional<SequencedDeadLetterQueue<EventMessage>> configuredDlq =
                config.eventProcessingConfiguration().deadLetterQueue(processingGroup);
        assertTrue(configuredDlq.isPresent());
        assertEquals(deadLetterQueue, configuredDlq.get());

        Optional<PooledStreamingEventProcessor> optionalProcessor =
                config.eventProcessingConfiguration()
                      .eventProcessor(processingGroup, PooledStreamingEventProcessor.class);
        assertTrue(optionalProcessor.isPresent());
        PooledStreamingEventProcessor resultProcessor = optionalProcessor.get();

        EventHandlerInvoker resultInvoker = (EventHandlerInvoker) ensureAccessible(
                resultProcessor.getClass().getDeclaredMethod("eventHandlerInvoker")
        ).invoke(resultProcessor);
        assertEquals(MultiEventHandlerInvoker.class, resultInvoker.getClass());

        MultiEventHandlerInvoker resultMultiInvoker = ((MultiEventHandlerInvoker) resultInvoker);
        List<EventHandlerInvoker> delegates = getField("delegates", resultMultiInvoker);
        assertFalse(delegates.isEmpty());
        DeadLetteringEventHandlerInvoker resultDeadLetteringInvoker =
                ((DeadLetteringEventHandlerInvoker) delegates.getFirst());

        assertEquals(expectedPolicy, getField("enqueuePolicy", resultDeadLetteringInvoker));
    }

    @Disabled("TODO #3517 - Revise Dead Letter Queue")
    @Test
    void registerDeadLetterPolicyIsUsed(@Mock SequencedDeadLetterQueue<EventMessage> deadLetterQueue)
            throws NoSuchFieldException, IllegalAccessException {
        String processingGroup = "pooled-streaming";
        EnqueuePolicy<EventMessage> expectedPolicy = (letter, cause) -> Decisions.ignore();
        EnqueuePolicy<EventMessage> unexpectedPolicy = (letter, cause) -> Decisions.evict();

        configurer.configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> deadLetterQueue)
                  .registerDeadLetterPolicy(processingGroup, c -> expectedPolicy)
                  .registerDeadLetterPolicy("unused-processing-group", c -> unexpectedPolicy);
        LegacyConfiguration config = configurer.start();

        Optional<EnqueuePolicy<EventMessage>> optionalPolicy = config.eventProcessingConfiguration()
                                                                        .deadLetterPolicy(processingGroup);
        assertTrue(optionalPolicy.isPresent());
        EnqueuePolicy<EventMessage> resultPolicy = optionalPolicy.get();
        assertEquals(expectedPolicy, resultPolicy);
        assertNotEquals(unexpectedPolicy, resultPolicy);

        Optional<SequencedDeadLetterQueue<EventMessage>> configuredDlq =
                config.eventProcessingConfiguration().deadLetterQueue(processingGroup);
        assertTrue(configuredDlq.isPresent());
        assertEquals(deadLetterQueue, configuredDlq.get());

        Optional<PooledStreamingEventProcessor> optionalProcessor =
                config.eventProcessingConfiguration()
                      .eventProcessor(processingGroup, PooledStreamingEventProcessor.class);
        assertTrue(optionalProcessor.isPresent());
        PooledStreamingEventProcessor resultProcessor = optionalProcessor.get();

//        DefaultEventProcessingPipeline operations = getField("eventProcessorOperations", resultProcessor);
//        EventHandlerInvoker resultInvoker = getField("eventHandlerInvoker", operations);
//        assertEquals(MultiEventHandlerInvoker.class, resultInvoker.getClass());
//
//        MultiEventHandlerInvoker resultMultiInvoker = ((MultiEventHandlerInvoker) resultInvoker);
//        List<EventHandlerInvoker> delegates = getField("delegates", resultMultiInvoker);
//        assertFalse(delegates.isEmpty());
//        DeadLetteringEventHandlerInvoker resultDeadLetteringInvoker =
//                ((DeadLetteringEventHandlerInvoker) delegates.getFirst());
//
//        assertEquals(expectedPolicy, getField("enqueuePolicy", resultDeadLetteringInvoker));
//        assertNotEquals(unexpectedPolicy, getField("enqueuePolicy", resultDeadLetteringInvoker));
    }

    @Disabled("TODO #3517 - Revise Dead Letter Queue")
    @Test
    void registeredDeadLetteringEventHandlerInvokerConfigurationIsUsed(
            @Mock SequencedDeadLetterQueue<EventMessage> deadLetterQueue
    ) throws NoSuchFieldException, IllegalAccessException {
        String processingGroup = "pooled-streaming";

        configurer.configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> deadLetterQueue)
                  .registerDeadLetteringEventHandlerInvokerConfiguration(
                          processingGroup, (config, builder) -> builder.allowReset(true)
                  );
        LegacyConfiguration config = configurer.start();

        Optional<SequencedDeadLetterQueue<EventMessage>> configuredDlq =
                config.eventProcessingConfiguration().deadLetterQueue(processingGroup);
        assertTrue(configuredDlq.isPresent());
        assertEquals(deadLetterQueue, configuredDlq.get());

        Optional<PooledStreamingEventProcessor> optionalProcessor =
                config.eventProcessingConfiguration()
                      .eventProcessor(processingGroup, PooledStreamingEventProcessor.class);
        assertTrue(optionalProcessor.isPresent());
        PooledStreamingEventProcessor resultProcessor = optionalProcessor.get();

//        DefaultEventProcessingPipeline operations = getField("eventProcessorOperations", resultProcessor);
//        EventHandlerInvoker resultInvoker = getField("eventHandlerInvoker", operations);
//        assertEquals(MultiEventHandlerInvoker.class, resultInvoker.getClass());
//
//        MultiEventHandlerInvoker resultMultiInvoker = ((MultiEventHandlerInvoker) resultInvoker);
//        List<EventHandlerInvoker> delegates = getField("delegates", resultMultiInvoker);
//        assertFalse(delegates.isEmpty());
//        DeadLetteringEventHandlerInvoker resultDeadLetteringInvoker =
//                ((DeadLetteringEventHandlerInvoker) delegates.getFirst());
//
//        assertTrue((Boolean) getField("allowReset", resultDeadLetteringInvoker));
    }

    @Disabled("#3710 Reenable after ParameterResolver fix")
    @Test
    void sequencedDeadLetterProcessorReturnsForProcessingGroupWithDlq(
            @Mock SequencedDeadLetterQueue<EventMessage> deadLetterQueue
    ) {
        String processingGroup = "pooled-streaming";
        String otherProcessingGroup = "tracking";

        configurer.configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerPooledStreamingEventProcessor(otherProcessingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerEventHandler(config -> new TrackingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> deadLetterQueue);

        LegacyConfiguration config = configurer.start();
        EventProcessingConfiguration eventProcessingConfig = config.eventProcessingConfiguration();

        Optional<SequencedDeadLetterQueue<EventMessage>> configuredDlq =
                eventProcessingConfig.deadLetterQueue(processingGroup);
        assertTrue(configuredDlq.isPresent());
        assertEquals(deadLetterQueue, configuredDlq.get());

        Optional<PooledStreamingEventProcessor> optionalProcessor =
                eventProcessingConfig.eventProcessor(processingGroup, PooledStreamingEventProcessor.class);
        assertTrue(optionalProcessor.isPresent());

        Optional<SequencedDeadLetterProcessor<EventMessage>> optionalDeadLetterProcessor =
                eventProcessingConfig.sequencedDeadLetterProcessor(processingGroup);
        assertTrue(optionalDeadLetterProcessor.isPresent());
        assertFalse(eventProcessingConfig.sequencedDeadLetterProcessor(otherProcessingGroup).isPresent());
        assertFalse(eventProcessingConfig.sequencedDeadLetterProcessor("non-existing-group").isPresent());
    }

    @Test
    void interceptorsOnDeadLetterProcessorShouldBePresent(
            @Mock SequencedDeadLetterQueue<EventMessage> deadLetterQueue
    ) throws NoSuchFieldException, IllegalAccessException {
        String processingGroup = "pooled-streaming";
        StubInterceptor interceptor1 = new StubInterceptor();
        StubInterceptor interceptor2 = new StubInterceptor();

        configurer.configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> deadLetterQueue)
                  .registerHandlerInterceptor(processingGroup, c -> interceptor1)
                  .registerDefaultHandlerInterceptor((c, n) -> interceptor2);

        LegacyConfiguration config = configurer.start();
        EventProcessingConfiguration eventProcessingConfig = config.eventProcessingConfiguration();

        Optional<SequencedDeadLetterProcessor<EventMessage>> optionalDeadLetterProcessor =
                eventProcessingConfig.sequencedDeadLetterProcessor(processingGroup);
        assertTrue(optionalDeadLetterProcessor.isPresent());
        List<MessageHandlerInterceptor<?>> interceptors = getField("interceptors", optionalDeadLetterProcessor.get());
        assertEquals(3, interceptors.size());
    }

    @Disabled("TODO #3517 - Revise Dead Letter Queue")
    @Test
    void registerDeadLetterQueueProviderConstructsDeadLetteringEventHandlerInvoker(
            @Mock SequencedDeadLetterQueue<EventMessage> deadLetterQueue
    ) throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        String processingGroup = "pooled-streaming";

        configurer.configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueueProvider(p -> c -> deadLetterQueue);
        LegacyConfiguration config = configurer.start();

        Optional<EnqueuePolicy<EventMessage>> optionalPolicy = config.eventProcessingConfiguration()
                                                                        .deadLetterPolicy(processingGroup);
        assertTrue(optionalPolicy.isPresent());
        EnqueuePolicy<EventMessage> expectedPolicy = optionalPolicy.get();

        Optional<SequencedDeadLetterQueue<EventMessage>> configuredDlq =
                config.eventProcessingConfiguration().deadLetterQueue(processingGroup);
        assertTrue(configuredDlq.isPresent());
        assertEquals(deadLetterQueue, configuredDlq.get());

        Optional<PooledStreamingEventProcessor> optionalProcessor =
                config.eventProcessingConfiguration()
                      .eventProcessor(processingGroup, PooledStreamingEventProcessor.class);
        assertTrue(optionalProcessor.isPresent());
        PooledStreamingEventProcessor resultProcessor = optionalProcessor.get();

//        DefaultEventProcessingPipeline operations = getField("eventProcessorOperations", resultProcessor);
//        EventHandlerInvoker resultInvoker = getField("eventHandlerInvoker", operations);
//        assertEquals(MultiEventHandlerInvoker.class, resultInvoker.getClass());
//
//        MultiEventHandlerInvoker resultMultiInvoker = ((MultiEventHandlerInvoker) resultInvoker);
//        List<EventHandlerInvoker> delegates = getField("delegates", resultMultiInvoker);
//        assertFalse(delegates.isEmpty());
//        DeadLetteringEventHandlerInvoker resultDeadLetteringInvoker =
//                ((DeadLetteringEventHandlerInvoker) delegates.getFirst());
//
//        assertEquals(deadLetterQueue, getField("queue", resultDeadLetteringInvoker));
//        assertEquals(expectedPolicy, getField("enqueuePolicy", resultDeadLetteringInvoker));
//        assertEquals(NoTransactionManager.INSTANCE, getField("transactionManager", resultDeadLetteringInvoker));
    }

    @Test
    void whenADeadLetterHasBeenRegisteredForASpecificGroupItWillBeUsedInsteadOfTheGenericOne(
            @Mock SequencedDeadLetterQueue<EventMessage> specificDeadLetterQueue,
            @Mock SequencedDeadLetterQueue<EventMessage> genericDeadLetterQueue
    ) {
        String processingGroup = "pooled-streaming";

        configurer.configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> specificDeadLetterQueue)
                  .registerDeadLetterQueueProvider(p -> c -> genericDeadLetterQueue);
        LegacyConfiguration config = configurer.start();

        Optional<SequencedDeadLetterQueue<EventMessage>> configuredDlq =
                config.eventProcessingConfiguration().deadLetterQueue(processingGroup);
        assertTrue(configuredDlq.isPresent());
        assertEquals(specificDeadLetterQueue, configuredDlq.get());
    }

    private <O, R> R getField(String fieldName, O object) throws NoSuchFieldException, IllegalAccessException {
        return getField(object.getClass(), fieldName, object);
    }

    private <C, O, R> R getField(Class<C> clazz,
                                 String fieldName,
                                 O object) throws NoSuchFieldException, IllegalAccessException {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        //noinspection unchecked
        return (R) field.get(object);
    }

    @SuppressWarnings("WeakerAccess")
    private static class StubEventProcessor implements EventProcessor {

        private final String name;
        private final EventHandlerInvoker eventHandlerInvoker;
        private final List<MessageHandlerInterceptor<EventMessage>> interceptors = new ArrayList<>();

        public StubEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker) {
            this.name = name;
            this.eventHandlerInvoker = eventHandlerInvoker;
        }

        @Override
        public String name() {
            return name;
        }

        public EventHandlerInvoker getEventHandlerInvoker() {
            return eventHandlerInvoker;
        }

        public List<?> getEventHandlers() {
            List<EventHandlerInvoker> invokers = ((MultiEventHandlerInvoker) getEventHandlerInvoker()).delegates();
            return ((SimpleEventHandlerInvoker) invokers.getFirst())
                    .eventHandlers()
                    .stream()
                    .map(eventHandlingComponent -> {
                        try {
                            Field handlerField =
                                    AnnotationEventHandlerAdapter.class.getDeclaredField("annotatedEventListener");
                            return ReflectionUtils.getFieldValue(handlerField, eventHandlingComponent);
                        } catch (NoSuchFieldException e) {
                            return null;
                        }
                    })
                    .collect(Collectors.toList());
        }

        @Override
        public CompletableFuture<Void> start() {
            return FutureUtils.emptyCompletedFuture();
        }

        @Override
        public CompletableFuture<Void> shutdown() {
            return FutureUtils.emptyCompletedFuture();
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public boolean isError() {
            return false;
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            throw new UnsupportedOperationException("Not required for tests.");
        }
    }

    @SuppressWarnings("WeakerAccess")
    @ProcessingGroup("processingGroup")
    public static class AnnotatedBean {

    }

    @SuppressWarnings("WeakerAccess")
    public static class AnnotatedBeanSubclass extends AnnotatedBean {

    }

    private static class StubInterceptor implements MessageHandlerInterceptor<EventMessage> {


        @Nonnull
        @Override
        public MessageStream<?> interceptOnHandle(@Nonnull EventMessage message,
                                                  @Nonnull ProcessingContext context,
                                                  @Nonnull MessageHandlerInterceptorChain<EventMessage> interceptorChain) {
            return interceptorChain.proceed(message, context);
        }
    }

    @SuppressWarnings("unused")
    @ProcessingGroup("subscribing")
    private static class SubscribingEventHandler {

        @EventHandler
        public void handle(Integer event, LegacyUnitOfWork<?> unitOfWork) {
            throw new IllegalStateException();
        }

        @EventHandler
        public void handle(Boolean event) {
            throw new IllegalStateException();
        }
    }

    @SuppressWarnings("unused")
    @ProcessingGroup("tracking")
    private static class TrackingEventHandler {

        @EventHandler
        public void handle(String event) {

        }

        @EventHandler
        public void handle(Integer event, LegacyUnitOfWork<?> unitOfWork) {
            throw new IllegalStateException();
        }

        @EventHandler
        public void handle(Boolean event) {
            throw new IllegalStateException();
        }
    }

    @SuppressWarnings("unused")
    @ProcessingGroup("pooled-streaming")
    private static class PooledStreamingEventHandler {

        @EventHandler
        public void handle(String event) {

        }
    }

    private static class StubErrorHandler implements ErrorHandler, ListenerInvocationErrorHandler {

        private final CountDownLatch latch;
        private final AtomicInteger errorCounter = new AtomicInteger();

        private StubErrorHandler(int count) {
            this.latch = new CountDownLatch(count);
        }

        @Override
        public void handleError(@Nonnull ErrorContext errorContext) {
            errorCounter.incrementAndGet();
            latch.countDown();
        }

        @Override
        public void onError(@Nonnull Exception exception, @Nonnull EventMessage event,
                            @Nonnull EventMessageHandler eventHandler) {
            errorCounter.incrementAndGet();
            latch.countDown();
        }

        @SuppressWarnings("WeakerAccess")
        public int getErrorCounter() {
            return errorCounter.get();
        }

        public boolean await(long timeout, TimeUnit timeUnit) throws InterruptedException {
            return latch.await(timeout, timeUnit);
        }
    }

    @ProcessingGroup("my-saga-processing-group")
    private static class CustomSaga {

    }

    private static class StubTransactionManager implements TransactionManager {

        private final CountDownLatch transactionCommitted;

        private StubTransactionManager(CountDownLatch transactionCommitted) {
            this.transactionCommitted = transactionCommitted;
        }

        @Override
        public Transaction startTransaction() {
            return new Transaction() {
                @Override
                public void commit() {
                    transactionCommitted.countDown();
                }

                @Override
                public void rollback() {

                }
            };
        }
    }
}
