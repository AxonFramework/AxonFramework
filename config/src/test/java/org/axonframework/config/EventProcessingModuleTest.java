/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.Registration;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.AbstractEventProcessor;
import org.axonframework.eventhandling.AnnotationEventHandlerAdapter;
import org.axonframework.eventhandling.ErrorContext;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.LoggingErrorHandler;
import org.axonframework.eventhandling.MultiEventHandlerInvoker;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.async.FullConcurrencyPolicy;
import org.axonframework.eventhandling.async.SequentialPolicy;
import org.axonframework.eventhandling.deadletter.DeadLetteringEventHandlerInvoker;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.lifecycle.LifecycleHandlerInvocationException;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import static org.axonframework.common.ReflectionUtils.getFieldValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link EventProcessingModule}.
 *
 * @author Allard Buijze
 */
@ExtendWith(MockitoExtension.class)
class EventProcessingModuleTest {

    private Configurer configurer;

    @BeforeEach
    void setUp() {
        configurer = DefaultConfigurer.defaultConfiguration();
    }

    @Test
    void testAssignmentRules() {
        Map<String, StubEventProcessor> processors = new HashMap<>();
        ConcurrentHashMap<Object, Object> map = new ConcurrentHashMap<>();
        AnnotatedBean annotatedBean = new AnnotatedBean();
        AnnotatedBeanSubclass annotatedBeanSubclass = new AnnotatedBeanSubclass();

        configurer.eventProcessing()
                  .registerEventProcessorFactory((name, config, eventHandlerInvoker) -> {
                      StubEventProcessor processor =
                              new StubEventProcessor(name, eventHandlerInvoker);
                      processors.put(name, processor);
                      return processor;
                  })
                  .assignHandlerInstancesMatching("java.util.concurrent", "concurrent"::equals)
                  .registerEventHandler(c -> new Object()) // --> java.lang
                  .registerEventHandler(c -> "") // --> java.lang
                  .registerEventHandler(c -> "concurrent") // --> java.util.concurrent
                  .registerEventHandler(c -> map) // --> java.util.concurrent
                  .registerEventHandler(c -> annotatedBean)
                  .registerEventHandler(c -> annotatedBeanSubclass);
        Configuration configuration = configurer.start();

        assertEquals(3, configuration.eventProcessingConfiguration().eventProcessors().size());
        assertTrue(processors.get("java.util.concurrent").getEventHandlers().contains("concurrent"));
        assertTrue(processors.get("java.util.concurrent").getEventHandlers().contains(map));
        assertTrue(processors.get("java.lang").getEventHandlers().contains(""));
        assertTrue(processors.get("processingGroup").getEventHandlers().contains(annotatedBean));
        assertTrue(processors.get("processingGroup").getEventHandlers().contains(annotatedBeanSubclass));
    }

    @Test
    void testByTypeAssignmentRules() {
        Map<String, StubEventProcessor> processors = new HashMap<>();
        ConcurrentHashMap<Object, Object> map = new ConcurrentHashMap<>();
        AnnotatedBean annotatedBean = new AnnotatedBean();
        AnnotatedBeanSubclass annotatedBeanSubclass = new AnnotatedBeanSubclass();

        configurer.eventProcessing()
                  .registerEventProcessorFactory((name, config, eventHandlerInvoker) -> {
                      StubEventProcessor processor =
                              new StubEventProcessor(name, eventHandlerInvoker);
                      processors.put(name, processor);
                      return processor;
                  })
                  .assignHandlerTypesMatching("special", ConcurrentHashMap.class::isAssignableFrom)
                  .registerEventHandler(c -> new Object()) // --> java.lang
                  .registerEventHandler(c -> "") // --> java.lang
                  .registerEventHandler(c -> "concurrent") // --> java.lang
                  .registerEventHandler(c -> map) // --> java.util.concurrent
                  .registerEventHandler(c -> annotatedBean)
                  .registerEventHandler(c -> annotatedBeanSubclass);
        Configuration configuration = configurer.start();

        assertEquals(3, configuration.eventProcessingConfiguration().eventProcessors().size());
        assertTrue(processors.get("java.lang").getEventHandlers().contains("concurrent"));
        assertTrue(processors.get("special").getEventHandlers().contains(map));
        assertTrue(processors.get("java.lang").getEventHandlers().contains(""));
        assertTrue(processors.get("processingGroup").getEventHandlers().contains(annotatedBean));
        assertTrue(processors.get("processingGroup").getEventHandlers().contains(annotatedBeanSubclass));
    }

    @Test
    void testProcessorsDefaultToSubscribingWhenUsingSimpleEventBus() {
        Configuration configuration = DefaultConfigurer.defaultConfiguration()
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

    @Test
    void testAssigningATrackingProcessorFailsWhenUsingSimpleEventBus() {
        Configurer configurer = DefaultConfigurer.defaultConfiguration()
                                                 .configureEventBus(c -> SimpleEventBus.builder().build())
                                                 .eventProcessing(ep -> ep.registerEventHandler(c -> new SubscribingEventHandler())
                                                                          .registerEventHandler(c -> new TrackingEventHandler())
                                                                          .registerTrackingEventProcessor("tracking"));

        assertThrows(LifecycleHandlerInvocationException.class, configurer::start);
    }

    @Test
    void testAssignmentRulesOverrideThoseWithLowerPriority() {
        Map<String, StubEventProcessor> processors = new HashMap<>();
        ConcurrentHashMap<Object, Object> map = new ConcurrentHashMap<>();
        configurer.eventProcessing()
                  .registerEventProcessorFactory((name, config, handlers) -> {
                      StubEventProcessor processor = new StubEventProcessor(name, handlers);
                      processors.put(name, processor);
                      return processor;
                  })
                  .assignHandlerInstancesMatching("java.util.concurrent", "concurrent"::equals)
                  .assignHandlerInstancesMatching("java.util.concurrent2",
                                                  1,
                                                  "concurrent"::equals)
                  .registerEventHandler(c -> new Object()) // --> java.lang
                  .registerEventHandler(c -> "") // --> java.lang
                  .registerEventHandler(c -> "concurrent") // --> java.util.concurrent2
                  .registerEventHandler(c -> map); // --> java.util.concurrent
        Configuration configuration = configurer.start();

        assertEquals(3, configuration.eventProcessingConfiguration().eventProcessors().size());
        assertTrue(processors.get("java.util.concurrent2").getEventHandlers().contains("concurrent"));
        assertTrue(processors.get("java.util.concurrent2").getHandlerInterceptors().iterator()
                             .next() instanceof CorrelationDataInterceptor);
        assertTrue(processors.get("java.util.concurrent").getEventHandlers().contains(map));
        assertTrue(processors.get("java.util.concurrent").getHandlerInterceptors().iterator()
                             .next() instanceof CorrelationDataInterceptor);
        assertTrue(processors.get("java.lang").getEventHandlers().contains(""));
        assertTrue(processors.get("java.lang").getHandlerInterceptors().iterator()
                             .next() instanceof CorrelationDataInterceptor);
    }

    @Test
    void testDefaultAssignToKeepsAnnotationScanning() {
        Map<String, StubEventProcessor> processors = new HashMap<>();
        AnnotatedBean annotatedBean = new AnnotatedBean();
        Object object = new Object();
        configurer.eventProcessing()
                  .registerEventProcessorFactory((name, config, handlers) -> {
                      StubEventProcessor processor = new StubEventProcessor(name, handlers);
                      processors.put(name, processor);
                      return processor;
                  })
                  .assignHandlerInstancesMatching("java.util.concurrent", "concurrent"::equals)
                  .byDefaultAssignTo("default")
                  .registerEventHandler(c -> object)        // --> default
                  .registerEventHandler(c -> "concurrent")  // --> java.util.concurrent
                  .registerEventHandler(c -> annotatedBean); // --> processingGroup
        configurer.start();

        assertEquals(3, processors.size());
        assertTrue(processors.get("default").getEventHandlers().contains(object));
        assertTrue(processors.get("java.util.concurrent").getEventHandlers().contains("concurrent"));
        assertTrue(processors.get("processingGroup").getEventHandlers().contains(annotatedBean));
    }

    @Test
    void testTypeAssignmentWithCustomDefault() {
        configurer.eventProcessing()
                  .assignHandlerTypesMatching("myGroup", String.class::equals)
                  .byDefaultAssignHandlerTypesTo(
                          t -> Object.class.equals(t) ? "obj" : t.getSimpleName() + "CustomProcessor"
                  )
                  .registerSaga(Object.class)
                  .registerSaga(ConcurrentMap.class)
                  .registerSaga(String.class)
                  .registerEventHandler(c -> new HashMap<>());
        EventProcessingConfiguration configuration = configurer.start()
                                                               .eventProcessingConfiguration();

        assertEquals("myGroup", configuration.sagaProcessingGroup(String.class));
        assertEquals("obj", configuration.sagaProcessingGroup(Object.class));
        assertEquals("ConcurrentMapCustomProcessor", configuration.sagaProcessingGroup(ConcurrentMap.class));

        assertEquals(4, configuration.eventProcessors().size());
        assertTrue(configuration.eventProcessor("myGroup").isPresent());
        assertTrue(configuration.eventProcessor("obj").isPresent());
        assertTrue(configuration.eventProcessor("java.util").isPresent());
        assertTrue(configuration.eventProcessor("ConcurrentMapCustomProcessor").isPresent());
    }

    @Test
    void testTypeAssignment() {
        configurer.eventProcessing()
                  .assignHandlerTypesMatching("myGroup", c -> "java.lang".equals(c.getPackage().getName()))
                  .registerSaga(Object.class)
                  .registerSaga(ConcurrentMap.class)
                  .registerSaga(String.class)
                  .registerEventHandler(c -> new HashMap<>());
        EventProcessingConfiguration configuration = configurer.start()
                                                               .eventProcessingConfiguration();

        assertEquals("myGroup", configuration.sagaProcessingGroup(String.class));
        assertEquals("myGroup", configuration.sagaProcessingGroup(Object.class));
        assertEquals("ConcurrentMapProcessor", configuration.sagaProcessingGroup(ConcurrentMap.class));

        assertEquals(3, configuration.eventProcessors().size());
        assertTrue(configuration.eventProcessor("myGroup").isPresent());
        assertTrue(configuration.eventProcessor("java.util").isPresent());
        assertTrue(configuration.eventProcessor("ConcurrentMapProcessor").isPresent());
    }

    @Test
    void testAssignSequencingPolicy() throws NoSuchFieldException {
        Object mockHandler = new Object();
        Object specialHandler = new Object();
        SequentialPolicy sequentialPolicy = new SequentialPolicy();
        FullConcurrencyPolicy fullConcurrencyPolicy = new FullConcurrencyPolicy();
        configurer.eventProcessing()
                  .registerEventHandler(c -> mockHandler)
                  .registerEventHandler(c -> specialHandler)
                  .assignHandlerInstancesMatching("special", specialHandler::equals)
                  .byDefaultAssignTo("default")
                  .registerDefaultSequencingPolicy(c -> sequentialPolicy)
                  .registerSequencingPolicy("special", c -> fullConcurrencyPolicy);
        Configuration config = configurer.start();

        Optional<AbstractEventProcessor> defaultProcessorOptional =
                config.eventProcessingConfiguration().eventProcessor("default", AbstractEventProcessor.class);
        assertTrue(defaultProcessorOptional.isPresent());
        AbstractEventProcessor defaultProcessor = defaultProcessorOptional.get();

        Optional<AbstractEventProcessor> specialProcessorOptional =
                config.eventProcessingConfiguration().eventProcessor("special", AbstractEventProcessor.class);
        assertTrue(specialProcessorOptional.isPresent());
        AbstractEventProcessor specialProcessor = specialProcessorOptional.get();

        MultiEventHandlerInvoker defaultInvoker =
                getFieldValue(AbstractEventProcessor.class.getDeclaredField("eventHandlerInvoker"), defaultProcessor);
        MultiEventHandlerInvoker specialInvoker =
                getFieldValue(AbstractEventProcessor.class.getDeclaredField("eventHandlerInvoker"), specialProcessor);

        assertEquals(sequentialPolicy,
                     ((SimpleEventHandlerInvoker) defaultInvoker.delegates().get(0)).getSequencingPolicy());
        assertEquals(fullConcurrencyPolicy,
                     ((SimpleEventHandlerInvoker) specialInvoker.delegates().get(0)).getSequencingPolicy());
    }

    @Test
    void testAssignInterceptors() {
        StubInterceptor interceptor1 = new StubInterceptor();
        StubInterceptor interceptor2 = new StubInterceptor();
        configurer.eventProcessing()
                  .registerEventProcessor("default", (name, config, handlers) -> new StubEventProcessor(name, handlers))
                  .byDefaultAssignTo("default")
                  .assignHandlerInstancesMatching("concurrent", 1, "concurrent"::equals)
                  .registerEventHandler(c -> new Object()) // --> java.lang
                  .registerEventHandler(c -> "concurrent") // --> java.util.concurrent2
                  .registerHandlerInterceptor("default", c -> interceptor1)
                  .registerDefaultHandlerInterceptor((c, n) -> interceptor2);
        Configuration config = configurer.start();

        // CorrelationDataInterceptor is automatically configured
        Optional<EventProcessor> defaultProcessor = config.eventProcessingConfiguration()
                                                          .eventProcessor("default");
        assertTrue(defaultProcessor.isPresent());
        assertEquals(3, defaultProcessor.get().getHandlerInterceptors().size());
    }

    @Test
    void testConfigureMonitor() throws Exception {
        MessageCollectingMonitor subscribingMonitor = new MessageCollectingMonitor();
        MessageCollectingMonitor trackingMonitor = new MessageCollectingMonitor(1);
        CountDownLatch tokenStoreInvocation = new CountDownLatch(1);

        buildComplexEventHandlingConfiguration(tokenStoreInvocation);
        configurer.eventProcessing()
                  .registerMessageMonitor("subscribing", c -> subscribingMonitor)
                  .registerMessageMonitor("tracking", c -> trackingMonitor);
        Configuration config = configurer.start();

        try {
            config.eventBus().publish(new GenericEventMessage<Object>("test"));

            assertEquals(1, subscribingMonitor.getMessages().size());
            assertTrue(trackingMonitor.await(10, TimeUnit.SECONDS));
            assertTrue(tokenStoreInvocation.await(10, TimeUnit.SECONDS));
        } finally {
            config.shutdown();
        }
    }

    @Test
    void testConfigureDefaultListenerInvocationErrorHandler() throws Exception {
        GenericEventMessage<Boolean> errorThrowingEventMessage = new GenericEventMessage<>(true);

        int expectedListenerInvocationErrorHandlerCalls = 2;

        StubErrorHandler errorHandler = new StubErrorHandler(2);
        CountDownLatch tokenStoreInvocation = new CountDownLatch(1);

        buildComplexEventHandlingConfiguration(tokenStoreInvocation);
        configurer.eventProcessing()
                  .registerDefaultListenerInvocationErrorHandler(config -> errorHandler);
        Configuration config = configurer.start();

        //noinspection Duplicates
        try {
            config.eventBus().publish(errorThrowingEventMessage);
            assertTrue(tokenStoreInvocation.await(10, TimeUnit.SECONDS));

            assertTrue(errorHandler.await(10, TimeUnit.SECONDS));
            assertEquals(expectedListenerInvocationErrorHandlerCalls, errorHandler.getErrorCounter());
        } finally {
            config.shutdown();
        }
    }

    @Test
    void testConfigureListenerInvocationErrorHandlerPerEventProcessor() throws Exception {
        GenericEventMessage<Boolean> errorThrowingEventMessage = new GenericEventMessage<>(true);

        int expectedErrorHandlerCalls = 1;

        StubErrorHandler subscribingErrorHandler = new StubErrorHandler(1);
        StubErrorHandler trackingErrorHandler = new StubErrorHandler(1);
        CountDownLatch tokenStoreInvocation = new CountDownLatch(1);

        buildComplexEventHandlingConfiguration(tokenStoreInvocation);
        configurer.eventProcessing()
                  .registerListenerInvocationErrorHandler("subscribing", config -> subscribingErrorHandler)
                  .registerListenerInvocationErrorHandler("tracking", config -> trackingErrorHandler);
        Configuration config = configurer.start();

        //noinspection Duplicates
        try {
            config.eventBus().publish(errorThrowingEventMessage);

            assertEquals(expectedErrorHandlerCalls, subscribingErrorHandler.getErrorCounter());

            assertTrue(tokenStoreInvocation.await(10, TimeUnit.SECONDS));
            assertTrue(trackingErrorHandler.await(10, TimeUnit.SECONDS));
            assertEquals(expectedErrorHandlerCalls, trackingErrorHandler.getErrorCounter());
        } finally {
            config.shutdown();
        }
    }

    @Test
    void testConfigureDefaultErrorHandler() throws Exception {
        GenericEventMessage<Integer> failingEventMessage = new GenericEventMessage<>(1000);

        int expectedErrorHandlerCalls = 2;

        StubErrorHandler errorHandler = new StubErrorHandler(2);
        CountDownLatch tokenStoreInvocation = new CountDownLatch(1);

        buildComplexEventHandlingConfiguration(tokenStoreInvocation);
        configurer.eventProcessing()
                  .registerDefaultListenerInvocationErrorHandler(c -> PropagatingErrorHandler.instance())
                  .registerDefaultErrorHandler(config -> errorHandler);
        Configuration config = configurer.start();

        //noinspection Duplicates
        try {
            config.eventBus().publish(failingEventMessage);
            assertTrue(tokenStoreInvocation.await(10, TimeUnit.SECONDS));

            assertTrue(errorHandler.await(10, TimeUnit.SECONDS));
            assertEquals(expectedErrorHandlerCalls, errorHandler.getErrorCounter());
        } finally {
            config.shutdown();
        }
    }

    @Test
    void testTrackingProcessorsUsesConfiguredDefaultStreamableMessageSource(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mock) {
        configurer.eventProcessing().configureDefaultStreamableMessageSource(c -> mock);
        configurer.eventProcessing().usingTrackingEventProcessors();
        configurer.registerEventHandler(c -> new TrackingEventHandler());

        Configuration config = configurer.start();
        Optional<TrackingEventProcessor> processor = config.eventProcessingConfiguration()
                                                           .eventProcessor("tracking", TrackingEventProcessor.class);
        assertTrue(processor.isPresent());
        assertEquals(mock, processor.get().getMessageSource());
    }

    @Test
    void testTrackingProcessorsUsesSpecificSource(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mock,
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mock2) {
        configurer.eventProcessing()
                  .configureDefaultStreamableMessageSource(c -> mock)
                  .registerTrackingEventProcessor("tracking", c -> mock2)
                  .registerEventHandler(c -> new TrackingEventHandler());

        Configuration config = configurer.start();
        Optional<TrackingEventProcessor> processor = config.eventProcessingConfiguration()
                                                           .eventProcessor("tracking", TrackingEventProcessor.class);
        assertTrue(processor.isPresent());
        assertEquals(mock2, processor.get().getMessageSource());
    }

    @Test
    void testSubscribingProcessorsUsesConfiguredDefaultSubscribableMessageSource(
            @Mock SubscribableMessageSource<EventMessage<?>> mock) {
        configurer.eventProcessing().configureDefaultSubscribableMessageSource(c -> mock);
        configurer.eventProcessing().usingSubscribingEventProcessors();
        configurer.registerEventHandler(c -> new SubscribingEventHandler());

        Configuration config = configurer.start();
        Optional<SubscribingEventProcessor> processor = config.eventProcessingConfiguration()
                                                              .eventProcessor("subscribing");
        assertTrue(processor.isPresent());
        assertEquals(mock, processor.get().getMessageSource());
    }

    @Test
    void testSubscribingProcessorsUsesSpecificSource(
            @Mock SubscribableMessageSource<EventMessage<?>> mock,
            @Mock SubscribableMessageSource<EventMessage<?>> mock2) {
        configurer.eventProcessing()
                  .configureDefaultSubscribableMessageSource(c -> mock)
                  .registerSubscribingEventProcessor("subscribing", c -> mock2)
                  .registerEventHandler(c -> new SubscribingEventHandler());

        Configuration config = configurer.start();
        Optional<SubscribingEventProcessor> processor = config.eventProcessingConfiguration()
                                                              .eventProcessor("subscribing");
        assertTrue(processor.isPresent());
        assertEquals(mock2, processor.get().getMessageSource());
    }


    @Test
    void testConfigureErrorHandlerPerEventProcessor() throws Exception {
        GenericEventMessage<Integer> failingEventMessage = new GenericEventMessage<>(1000);

        int expectedErrorHandlerCalls = 1;

        StubErrorHandler subscribingErrorHandler = new StubErrorHandler(1);
        StubErrorHandler trackingErrorHandler = new StubErrorHandler(1);
        CountDownLatch tokenStoreInvocation = new CountDownLatch(1);

        buildComplexEventHandlingConfiguration(tokenStoreInvocation);
        configurer.eventProcessing()
                  .registerDefaultListenerInvocationErrorHandler(c -> PropagatingErrorHandler.instance())
                  .registerErrorHandler("subscribing", config -> subscribingErrorHandler)
                  .registerErrorHandler("tracking", config -> trackingErrorHandler);
        Configuration config = configurer.start();

        //noinspection Duplicates
        try {
            config.eventBus().publish(failingEventMessage);

            assertEquals(expectedErrorHandlerCalls, subscribingErrorHandler.getErrorCounter());

            assertTrue(tokenStoreInvocation.await(10, TimeUnit.SECONDS));
            assertTrue(trackingErrorHandler.await(10, TimeUnit.SECONDS));
            assertEquals(expectedErrorHandlerCalls, trackingErrorHandler.getErrorCounter());
        } finally {
            config.shutdown();
        }
    }

    @Test
    void testPackageOfObject() {
        String expectedPackageName = EventProcessingModule.class.getPackage().getName();
        assertEquals(expectedPackageName, EventProcessingModule.packageOfObject(this));
    }

    @Test
    void testDefaultTrackingEventProcessingConfiguration(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSource
    ) throws NoSuchFieldException {
        Object someHandler = new Object();
        TrackingEventProcessorConfiguration testTepConfig =
                TrackingEventProcessorConfiguration.forParallelProcessing(4);
        configurer.eventProcessing()
                  .usingTrackingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> mockedSource)
                  .byDefaultAssignTo("default")
                  .registerEventHandler(config -> someHandler)
                  .registerEventHandler(config -> new TrackingEventHandler())
                  .registerTrackingEventProcessorConfiguration(config -> testTepConfig);
        Configuration config = configurer.start();

        Optional<TrackingEventProcessor> resultTrackingTep =
                config.eventProcessingConfiguration().eventProcessor("tracking", TrackingEventProcessor.class);
        assertTrue(resultTrackingTep.isPresent());
        TrackingEventProcessor trackingTep = resultTrackingTep.get();
        int trackingTepSegmentsSize =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), trackingTep);
        assertEquals(4, trackingTepSegmentsSize);


        Optional<TrackingEventProcessor> resultDefaultTep =
                config.eventProcessingConfiguration().eventProcessor("default", TrackingEventProcessor.class);
        assertTrue(resultDefaultTep.isPresent());
        TrackingEventProcessor defaultTep = resultDefaultTep.get();
        int defaultTepSegmentsSize =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), defaultTep);
        assertEquals(4, defaultTepSegmentsSize);
    }

    @Test
    void testCustomTrackingEventProcessingConfiguration(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSource
    ) throws NoSuchFieldException {
        Object someHandler = new Object();
        TrackingEventProcessorConfiguration testTepConfig =
                TrackingEventProcessorConfiguration.forParallelProcessing(4);
        configurer.eventProcessing()
                  .usingTrackingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> mockedSource)
                  .byDefaultAssignTo("default")
                  .registerEventHandler(config -> someHandler)
                  .registerEventHandler(config -> new TrackingEventHandler())
                  .registerTrackingEventProcessorConfiguration("tracking", config -> testTepConfig);
        Configuration config = configurer.start();

        Optional<TrackingEventProcessor> resultTrackingTep =
                config.eventProcessingConfiguration().eventProcessor("tracking", TrackingEventProcessor.class);
        assertTrue(resultTrackingTep.isPresent());
        TrackingEventProcessor trackingTep = resultTrackingTep.get();
        int trackingTepSegmentsSize =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), trackingTep);
        assertEquals(4, trackingTepSegmentsSize);

        Optional<TrackingEventProcessor> resultDefaultTep =
                config.eventProcessingConfiguration().eventProcessor("default", TrackingEventProcessor.class);
        assertTrue(resultDefaultTep.isPresent());
        TrackingEventProcessor defaultTep = resultDefaultTep.get();
        int defaultTepSegmentsSize =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), defaultTep);
        assertEquals(1, defaultTepSegmentsSize);
    }

    @Test
    void testSagaTrackingProcessorsDefaultsToSagaTrackingEventProcessorConfigIfNoCustomizationIsPresent(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSource,
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSourceForVerification
    ) throws NoSuchFieldException {
        configurer.eventProcessing()
                  .usingTrackingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> mockedSource)
                  .registerSaga(Object.class);
        Configuration config = configurer.start();

        Optional<TrackingEventProcessor> resultTep =
                config.eventProcessingConfiguration().eventProcessor("ObjectProcessor", TrackingEventProcessor.class);
        assertTrue(resultTep.isPresent());
        TrackingEventProcessor tep = resultTep.get();
        int tepSegmentsSize =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), tep);
        assertEquals(1, tepSegmentsSize);

        Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> tepInitialTokenBuilder =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("initialTrackingTokenBuilder"), tep);
        tepInitialTokenBuilder.apply(mockedSourceForVerification);
        verify(mockedSourceForVerification, times(0)).createTailToken();
        // The default Saga Config starts the stream at the head
        verify(mockedSourceForVerification).createHeadToken();
    }

    @Test
    void testSagaTrackingProcessorsDoesNotPickDefaultsSagaTrackingEventProcessorConfigForCustomProcessingGroup(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSource,
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSourceForVerification
    ) throws NoSuchFieldException {
        configurer.eventProcessing()
                  .usingTrackingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> mockedSource)
                  .registerSaga(CustomSaga.class);
        Configuration config = configurer.start();

        Optional<TrackingEventProcessor> resultTep = config.eventProcessingConfiguration().eventProcessor(
                "my-saga-processing-group", TrackingEventProcessor.class
        );
        assertTrue(resultTep.isPresent());
        TrackingEventProcessor tep = resultTep.get();
        int tepSegmentsSize =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), tep);
        assertEquals(1, tepSegmentsSize);

        Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> tepInitialTokenBuilder =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("initialTrackingTokenBuilder"), tep);
        tepInitialTokenBuilder.apply(mockedSourceForVerification);
        // In absence of the default Saga Config, the stream starts at the tail
        verify(mockedSourceForVerification).createTailToken();
        verify(mockedSourceForVerification, times(0)).createHeadToken();
    }

    @Test
    void testSagaTrackingProcessorsDoesNotPickDefaultsSagaTrackingEventProcessorConfigForCustomProcessor(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSource,
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSourceForVerification
    ) throws NoSuchFieldException {
        configurer.eventProcessing()
                  .assignProcessingGroup(someGroup -> "custom-processor")
                  .registerTrackingEventProcessor("custom-processor", config -> mockedSource)
                  .registerSaga(CustomSaga.class);
        Configuration config = configurer.start();

        Optional<TrackingEventProcessor> resultTep = config.eventProcessingConfiguration().eventProcessor(
                "custom-processor", TrackingEventProcessor.class
        );
        assertTrue(resultTep.isPresent());
        TrackingEventProcessor tep = resultTep.get();
        int tepSegmentsSize =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), tep);
        assertEquals(1, tepSegmentsSize);

        Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> tepInitialTokenBuilder =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("initialTrackingTokenBuilder"), tep);
        tepInitialTokenBuilder.apply(mockedSourceForVerification);
        // In absence of the default Saga Config, the stream starts at the tail
        verify(mockedSourceForVerification).createTailToken();
        verify(mockedSourceForVerification, times(0)).createHeadToken();
    }

    @Test
    void testSagaTrackingProcessorsDoesNotPickDefaultsSagaTrackingEventProcessorConfigForCustomTrackingProcessorBuilder(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSource,
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSourceForVerification
    ) throws NoSuchFieldException {
        TrackingEventProcessorConfiguration testTepConfig =
                TrackingEventProcessorConfiguration.forParallelProcessing(3);
        configurer.eventProcessing()
                  .registerTrackingEventProcessor("ObjectProcessor", config -> mockedSource, config -> testTepConfig)
                  .registerSaga(Object.class);
        Configuration config = configurer.start();

        Optional<TrackingEventProcessor> resultTep =
                config.eventProcessingConfiguration().eventProcessor("ObjectProcessor", TrackingEventProcessor.class);
        assertTrue(resultTep.isPresent());
        TrackingEventProcessor tep = resultTep.get();
        int tepSegmentsSize =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), tep);
        assertEquals(3, tepSegmentsSize);

        Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> tepInitialTokenBuilder =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("initialTrackingTokenBuilder"), tep);
        tepInitialTokenBuilder.apply(mockedSourceForVerification);
        // In absence of the default Saga Config, the stream starts at the tail
        verify(mockedSourceForVerification).createTailToken();
        verify(mockedSourceForVerification, times(0)).createHeadToken();
    }

    @Test
    void testSagaTrackingProcessorsDoesNotPickDefaultsSagaTrackingEventProcessorConfigForCustomConfigInstance(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSource,
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSourceForVerification
    ) throws NoSuchFieldException {
        TrackingEventProcessorConfiguration testTepConfig =
                TrackingEventProcessorConfiguration.forParallelProcessing(4);
        configurer.eventProcessing()
                  .usingTrackingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> mockedSource)
                  .registerSaga(Object.class)
                  .registerTrackingEventProcessorConfiguration("ObjectProcessor", config -> testTepConfig);
        Configuration config = configurer.start();

        Optional<TrackingEventProcessor> resultTep =
                config.eventProcessingConfiguration().eventProcessor("ObjectProcessor", TrackingEventProcessor.class);
        assertTrue(resultTep.isPresent());
        TrackingEventProcessor tep = resultTep.get();
        int tepSegmentsSize =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), tep);
        assertEquals(4, tepSegmentsSize);

        Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> tepInitialTokenBuilder =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("initialTrackingTokenBuilder"), tep);
        tepInitialTokenBuilder.apply(mockedSourceForVerification);
        // In absence of the default Saga Config, the stream starts at the tail
        verify(mockedSourceForVerification).createTailToken();
        verify(mockedSourceForVerification, times(0)).createHeadToken();
    }

    @Test
    void testDefaultPooledStreamingEventProcessingConfiguration(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSource
    ) {
        Object someHandler = new Object();
        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> mockedSource)
                  .byDefaultAssignTo("default")
                  .registerEventHandler(config -> someHandler)
                  .registerEventHandler(config -> new PooledStreamingEventHandler());
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> resultPooledPsep =
                config.eventProcessingConfiguration()
                      .eventProcessor("pooled-streaming", PooledStreamingEventProcessor.class);
        assertTrue(resultPooledPsep.isPresent());

        Optional<PooledStreamingEventProcessor> resultDefaultPsep =
                config.eventProcessingConfiguration().eventProcessor("default", PooledStreamingEventProcessor.class);
        assertTrue(resultDefaultPsep.isPresent());
    }

    @Test
    void testConfigurePooledStreamingEventProcessorFailsInAbsenceOfStreamableMessageSource() {
        String testName = "pooled-streaming";
        // This configurer does not contain an EventStore or other StreamableMessageSource.
        configurer.eventProcessing()
                  .registerPooledStreamingEventProcessor(testName)
                  .registerEventHandler(config -> new PooledStreamingEventHandler());
        assertThrows(LifecycleHandlerInvocationException.class, () -> configurer.start());
    }

    @Test
    void testConfigurePooledStreamingEventProcessor() throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        TokenStore testTokenStore = new InMemoryTokenStore();

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(testName)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerRollbackConfiguration(testName, config -> RollbackConfigurationType.ANY_THROWABLE)
                  .registerErrorHandler(testName, config -> PropagatingErrorHandler.INSTANCE)
                  .registerTokenStore(testName, config -> testTokenStore)
                  .registerTransactionManager(testName, config -> NoTransactionManager.INSTANCE);
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertTrue(optionalResult.isPresent());
        PooledStreamingEventProcessor result = optionalResult.get();
        assertEquals(testName, result.getName());
        assertEquals(
                RollbackConfigurationType.ANY_THROWABLE,
                getField(AbstractEventProcessor.class, "rollbackConfiguration", result)
        );
        assertEquals(PropagatingErrorHandler.INSTANCE, getField(AbstractEventProcessor.class, "errorHandler", result));
        assertEquals(testTokenStore, getField("tokenStore", result));
        assertEquals(NoTransactionManager.INSTANCE, getField("transactionManager", result));
    }

    @Test
    void testConfigurePooledStreamingEventProcessorWithSource(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSource
    ) throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        TokenStore testTokenStore = new InMemoryTokenStore();

        configurer.eventProcessing()
                  .registerPooledStreamingEventProcessor(testName, config -> mockedSource)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerRollbackConfiguration(testName, config -> RollbackConfigurationType.ANY_THROWABLE)
                  .registerErrorHandler(testName, config -> PropagatingErrorHandler.INSTANCE)
                  .registerTokenStore(testName, config -> testTokenStore)
                  .registerTransactionManager(testName, config -> NoTransactionManager.INSTANCE);
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertTrue(optionalResult.isPresent());
        PooledStreamingEventProcessor result = optionalResult.get();
        assertEquals(testName, result.getName());
        assertEquals(
                RollbackConfigurationType.ANY_THROWABLE,
                getField(AbstractEventProcessor.class, "rollbackConfiguration", result)
        );
        assertEquals(PropagatingErrorHandler.INSTANCE, getField(AbstractEventProcessor.class, "errorHandler", result));
        assertEquals(mockedSource, getField("messageSource", result));
        assertEquals(testTokenStore, getField("tokenStore", result));
        assertEquals(NoTransactionManager.INSTANCE, getField("transactionManager", result));
    }

    @Test
    void testConfigurePooledStreamingEventProcessorWithConfiguration(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSource
    ) throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        int testCapacity = 24;

        configurer.eventProcessing()
                  .registerPooledStreamingEventProcessor(
                          testName,
                          config -> mockedSource,
                          (config, builder) -> builder.maxClaimedSegments(testCapacity)
                  )
                  .registerEventHandler(config -> new PooledStreamingEventHandler());
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertTrue(optionalResult.isPresent());
        PooledStreamingEventProcessor result = optionalResult.get();
        assertEquals(testCapacity, result.maxCapacity());
        assertEquals(mockedSource, getField("messageSource", result));
    }

    @Test
    void testRegisterPooledStreamingEventProcessorConfigurationIsUsedDuringAllPsepConstructions(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSource
    ) throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        int testCapacity = 24;
        Object testHandler = new Object();

        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors()
                  .registerPooledStreamingEventProcessorConfiguration(
                          (config, builder) -> builder.maxClaimedSegments(testCapacity)
                  )
                  .configureDefaultStreamableMessageSource(config -> mockedSource)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .byDefaultAssignTo("default")
                  .registerEventHandler(config -> testHandler);
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertTrue(optionalResult.isPresent());
        PooledStreamingEventProcessor result = optionalResult.get();
        assertEquals(testCapacity, result.maxCapacity());
        assertEquals(mockedSource, getField("messageSource", result));

        optionalResult = config.eventProcessingConfiguration()
                               .eventProcessor("default", PooledStreamingEventProcessor.class);

        assertTrue(optionalResult.isPresent());
        result = optionalResult.get();
        assertEquals(testCapacity, result.maxCapacity());
        assertEquals(mockedSource, getField("messageSource", result));
    }

    @Test
    void testUsingPooledStreamingEventProcessorWithConfigurationIsUsedDuringAllPsepConstructions(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSource
    ) throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        int testCapacity = 24;
        Object testHandler = new Object();

        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors((config, builder) -> builder.maxClaimedSegments(testCapacity))
                  .configureDefaultStreamableMessageSource(config -> mockedSource)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .byDefaultAssignTo("default")
                  .registerEventHandler(config -> testHandler);
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertTrue(optionalResult.isPresent());
        PooledStreamingEventProcessor result = optionalResult.get();
        assertEquals(testCapacity, result.maxCapacity());
        assertEquals(mockedSource, getField("messageSource", result));

        optionalResult = config.eventProcessingConfiguration()
                               .eventProcessor("default", PooledStreamingEventProcessor.class);

        assertTrue(optionalResult.isPresent());
        result = optionalResult.get();
        assertEquals(testCapacity, result.maxCapacity());
        assertEquals(mockedSource, getField("messageSource", result));
    }

    @Test
    void testRegisterPooledStreamingEventProcessorConfigurationForNameIsUsedDuringSpecificPsepConstruction(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSource
    ) throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        int testCapacity = 24;
        Object testHandler = new Object();

        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors()
                  .registerPooledStreamingEventProcessorConfiguration(
                          "pooled-streaming", (config, builder) -> builder.maxClaimedSegments(testCapacity)
                  )
                  .configureDefaultStreamableMessageSource(config -> mockedSource)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .byDefaultAssignTo("default")
                  .registerEventHandler(config -> testHandler);
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertTrue(optionalResult.isPresent());
        PooledStreamingEventProcessor result = optionalResult.get();
        assertEquals(testCapacity, result.maxCapacity());
        assertEquals(mockedSource, getField("messageSource", result));

        optionalResult = config.eventProcessingConfiguration()
                               .eventProcessor("default", PooledStreamingEventProcessor.class);

        assertTrue(optionalResult.isPresent());
        result = optionalResult.get();
        assertEquals(Short.MAX_VALUE, result.maxCapacity());
        assertEquals(mockedSource, getField("messageSource", result));
    }

    @Test
    void testRegisterPooledStreamingEventProcessorWithConfigurationOverridesDefaultPsepConfiguration(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSource
    ) throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        int testCapacity = 24;
        int incorrectCapacity = 1745;

        configurer.eventProcessing()
                  .registerPooledStreamingEventProcessorConfiguration(
                          (config, builder) -> builder.maxClaimedSegments(incorrectCapacity)
                  )
                  .registerPooledStreamingEventProcessor(
                          testName,
                          config -> mockedSource,
                          (config, builder) -> builder.maxClaimedSegments(testCapacity)
                  )
                  .registerEventHandler(config -> new PooledStreamingEventHandler());
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertTrue(optionalResult.isPresent());
        PooledStreamingEventProcessor result = optionalResult.get();
        assertEquals(testCapacity, result.maxCapacity());
        assertEquals(mockedSource, getField("messageSource", result));
    }

    @Test
    void testRegisterPooledStreamingEventProcessorWithConfigurationOverridesCustomPsepConfiguration(
            @Mock StreamableMessageSource<TrackedEventMessage<?>> mockedSource
    ) throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        int testCapacity = 24;
        int wrongCapacity = 42;
        int incorrectCapacity = 1729;

        configurer.eventProcessing()
                  .registerPooledStreamingEventProcessorConfiguration(
                          (config, builder) -> builder.batchSize(100).maxClaimedSegments(wrongCapacity)
                  )
                  .registerPooledStreamingEventProcessorConfiguration(
                          "pooled-streaming", (config, builder) -> builder.maxClaimedSegments(incorrectCapacity)
                  )
                  .registerPooledStreamingEventProcessor(
                          testName,
                          config -> mockedSource,
                          (config, builder) -> builder.maxClaimedSegments(testCapacity)
                  )
                  .registerEventHandler(config -> new PooledStreamingEventHandler());
        Configuration config = configurer.buildConfiguration();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertTrue(optionalResult.isPresent());
        PooledStreamingEventProcessor result = optionalResult.get();
        assertEquals(testCapacity, result.maxCapacity());
        assertEquals(mockedSource, getField("messageSource", result));
        assertEquals(100, (int) getField("batchSize", result));
    }

    @Test
    void testDefaultTransactionManagerIsUsedUponEventProcessorConstruction() throws InterruptedException {
        String testName = "pooled-streaming";
        GenericEventMessage<Integer> testEvent = new GenericEventMessage<>(1000);

        CountDownLatch transactionCommitted = new CountDownLatch(1);
        TransactionManager defaultTransactionManager = new StubTransactionManager(transactionCommitted);

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(testName)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDefaultTransactionManager(c -> defaultTransactionManager);
        Configuration config = configurer.start();

        try {
            config.eventBus().publish(testEvent);
            assertTrue(transactionCommitted.await(10, TimeUnit.SECONDS));
        } finally {
            config.shutdown();
        }
    }

    @Test
    void testDefaultTransactionManagerIsOverriddenByProcessorSpecificInstance() throws InterruptedException {
        String testName = "pooled-streaming";
        GenericEventMessage<Integer> testEvent = new GenericEventMessage<>(1000);

        TransactionManager defaultTransactionManager = spy(TransactionManager.class);
        CountDownLatch transactionCommitted = new CountDownLatch(1);
        TransactionManager processorSpecificTransactionManager = new StubTransactionManager(transactionCommitted);

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(testName)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDefaultTransactionManager(c -> defaultTransactionManager)
                  .registerTransactionManager(testName, c -> processorSpecificTransactionManager);
        Configuration config = configurer.start();

        try {
            config.eventBus().publish(testEvent);
            assertTrue(transactionCommitted.await(10, TimeUnit.SECONDS));
        } finally {
            config.shutdown();
        }
        verifyNoInteractions(defaultTransactionManager);
    }

    @Test
    void testRegisterDeadLetterQueueConstructsDeadLetteringEventHandlerInvoker(
            @Mock SequencedDeadLetterQueue<DeadLetter<EventMessage<?>>> deadLetterQueue
    ) throws NoSuchFieldException, IllegalAccessException {
        String processingGroup = "pooled-streaming";

        ListenerInvocationErrorHandler listenerInvocationErrorHandler = new LoggingErrorHandler();

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> deadLetterQueue)
                  .registerTransactionManager(processingGroup, c -> NoTransactionManager.INSTANCE)
                  .registerListenerInvocationErrorHandler(processingGroup, c -> listenerInvocationErrorHandler);
        Configuration config = configurer.start();

        Optional<EnqueuePolicy<DeadLetter<EventMessage<?>>>> optionalPolicy = config.eventProcessingConfiguration()
                                                                                    .enqueuePolicy(processingGroup);
        assertTrue(optionalPolicy.isPresent());
        EnqueuePolicy<DeadLetter<EventMessage<?>>> expectedPolicy = optionalPolicy.get();

        Optional<SequencedDeadLetterQueue<DeadLetter<EventMessage<?>>>> configuredDlq =
                config.eventProcessingConfiguration().deadLetterQueue(processingGroup);
        assertTrue(configuredDlq.isPresent());
        assertEquals(deadLetterQueue, configuredDlq.get());

        Optional<PooledStreamingEventProcessor> optionalProcessor =
                config.eventProcessingConfiguration()
                      .eventProcessor(processingGroup, PooledStreamingEventProcessor.class);
        assertTrue(optionalProcessor.isPresent());
        PooledStreamingEventProcessor resultProcessor = optionalProcessor.get();

        EventHandlerInvoker resultInvoker =
                getField(AbstractEventProcessor.class, "eventHandlerInvoker", resultProcessor);
        assertEquals(MultiEventHandlerInvoker.class, resultInvoker.getClass());

        MultiEventHandlerInvoker resultMultiInvoker = ((MultiEventHandlerInvoker) resultInvoker);
        List<EventHandlerInvoker> delegates = getField("delegates", resultMultiInvoker);
        assertFalse(delegates.isEmpty());
        DeadLetteringEventHandlerInvoker resultDeadLetteringInvoker =
                ((DeadLetteringEventHandlerInvoker) delegates.get(0));

        assertEquals(processingGroup, getField("processingGroup", resultDeadLetteringInvoker));
        assertEquals(deadLetterQueue, getField("queue", resultDeadLetteringInvoker));
        assertEquals(expectedPolicy, getField("enqueuePolicy", resultDeadLetteringInvoker));
        assertEquals(NoTransactionManager.INSTANCE, getField("transactionManager", resultDeadLetteringInvoker));
        assertEquals(listenerInvocationErrorHandler,
                     getField("listenerInvocationErrorHandler", resultDeadLetteringInvoker));
    }

    @Test
    void testRegisterDefaultEnqueuePolicy(
            @Mock SequencedDeadLetterQueue<DeadLetter<EventMessage<?>>> deadLetterQueue
    ) throws NoSuchFieldException, IllegalAccessException {
        String processingGroup = "pooled-streaming";
        EnqueuePolicy<DeadLetter<EventMessage<?>>> expectedPolicy = (letter, cause) -> Decisions.ignore();

        ListenerInvocationErrorHandler listenerInvocationErrorHandler = new LoggingErrorHandler();

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> deadLetterQueue)
                  .registerDefaultEnqueuePolicy(c -> expectedPolicy)
                  .registerTransactionManager(processingGroup, c -> NoTransactionManager.INSTANCE)
                  .registerListenerInvocationErrorHandler(processingGroup, c -> listenerInvocationErrorHandler);
        Configuration config = configurer.start();

        Optional<EnqueuePolicy<DeadLetter<EventMessage<?>>>> optionalPolicy = config.eventProcessingConfiguration()
                                                                                    .enqueuePolicy(processingGroup);
        assertTrue(optionalPolicy.isPresent());
        EnqueuePolicy<DeadLetter<EventMessage<?>>> resultPolicy = optionalPolicy.get();
        assertEquals(expectedPolicy, resultPolicy);

        Optional<SequencedDeadLetterQueue<DeadLetter<EventMessage<?>>>> configuredDlq =
                config.eventProcessingConfiguration().deadLetterQueue(processingGroup);
        assertTrue(configuredDlq.isPresent());
        assertEquals(deadLetterQueue, configuredDlq.get());

        Optional<PooledStreamingEventProcessor> optionalProcessor =
                config.eventProcessingConfiguration()
                      .eventProcessor(processingGroup, PooledStreamingEventProcessor.class);
        assertTrue(optionalProcessor.isPresent());
        PooledStreamingEventProcessor resultProcessor = optionalProcessor.get();

        EventHandlerInvoker resultInvoker =
                getField(AbstractEventProcessor.class, "eventHandlerInvoker", resultProcessor);
        assertEquals(MultiEventHandlerInvoker.class, resultInvoker.getClass());

        MultiEventHandlerInvoker resultMultiInvoker = ((MultiEventHandlerInvoker) resultInvoker);
        List<EventHandlerInvoker> delegates = getField("delegates", resultMultiInvoker);
        assertFalse(delegates.isEmpty());
        DeadLetteringEventHandlerInvoker resultDeadLetteringInvoker =
                ((DeadLetteringEventHandlerInvoker) delegates.get(0));

        assertEquals(processingGroup, getField("processingGroup", resultDeadLetteringInvoker));
        assertEquals(expectedPolicy, getField("enqueuePolicy", resultDeadLetteringInvoker));
    }

    @Test
    void testRegisterEnqueuePolicy(
            @Mock SequencedDeadLetterQueue<DeadLetter<EventMessage<?>>> deadLetterQueue
    ) throws NoSuchFieldException, IllegalAccessException {
        String processingGroup = "pooled-streaming";
        EnqueuePolicy<DeadLetter<EventMessage<?>>> expectedPolicy = (letter, cause) -> Decisions.ignore();
        EnqueuePolicy<DeadLetter<EventMessage<?>>> unexpectedPolicy = (letter, cause) -> Decisions.evict();

        ListenerInvocationErrorHandler listenerInvocationErrorHandler = new LoggingErrorHandler();

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> deadLetterQueue)
                  .registerEnqueuePolicy(processingGroup, c -> expectedPolicy)
                  .registerEnqueuePolicy("unused-processing-group", c -> unexpectedPolicy)
                  .registerTransactionManager(processingGroup, c -> NoTransactionManager.INSTANCE)
                  .registerListenerInvocationErrorHandler(processingGroup, c -> listenerInvocationErrorHandler);
        Configuration config = configurer.start();

        Optional<EnqueuePolicy<DeadLetter<EventMessage<?>>>> optionalPolicy = config.eventProcessingConfiguration()
                                                                                    .enqueuePolicy(processingGroup);
        assertTrue(optionalPolicy.isPresent());
        EnqueuePolicy<DeadLetter<EventMessage<?>>> resultPolicy = optionalPolicy.get();
        assertEquals(expectedPolicy, resultPolicy);
        assertNotEquals(unexpectedPolicy, resultPolicy);

        Optional<SequencedDeadLetterQueue<DeadLetter<EventMessage<?>>>> configuredDlq =
                config.eventProcessingConfiguration().deadLetterQueue(processingGroup);
        assertTrue(configuredDlq.isPresent());
        assertEquals(deadLetterQueue, configuredDlq.get());

        Optional<PooledStreamingEventProcessor> optionalProcessor =
                config.eventProcessingConfiguration()
                      .eventProcessor(processingGroup, PooledStreamingEventProcessor.class);
        assertTrue(optionalProcessor.isPresent());
        PooledStreamingEventProcessor resultProcessor = optionalProcessor.get();

        EventHandlerInvoker resultInvoker =
                getField(AbstractEventProcessor.class, "eventHandlerInvoker", resultProcessor);
        assertEquals(MultiEventHandlerInvoker.class, resultInvoker.getClass());

        MultiEventHandlerInvoker resultMultiInvoker = ((MultiEventHandlerInvoker) resultInvoker);
        List<EventHandlerInvoker> delegates = getField("delegates", resultMultiInvoker);
        assertFalse(delegates.isEmpty());
        DeadLetteringEventHandlerInvoker resultDeadLetteringInvoker =
                ((DeadLetteringEventHandlerInvoker) delegates.get(0));

        assertEquals(processingGroup, getField("processingGroup", resultDeadLetteringInvoker));
        assertEquals(expectedPolicy, getField("enqueuePolicy", resultDeadLetteringInvoker));
        assertNotEquals(unexpectedPolicy, getField("enqueuePolicy", resultDeadLetteringInvoker));
    }

    @Test
    void testRegisterDeadLetteringEventHandlerInvokerConfiguration(
            @Mock SequencedDeadLetterQueue<DeadLetter<EventMessage<?>>> deadLetterQueue
    ) throws NoSuchFieldException, IllegalAccessException {
        String processingGroup = "pooled-streaming";

        ListenerInvocationErrorHandler listenerInvocationErrorHandler = new LoggingErrorHandler();

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> deadLetterQueue)
                  .registerDeadLetteringEventHandlerInvokerConfiguration(
                          processingGroup, (config, builder) -> builder.allowReset(true)
                  )
                  .registerTransactionManager(processingGroup, c -> NoTransactionManager.INSTANCE)
                  .registerListenerInvocationErrorHandler(processingGroup, c -> listenerInvocationErrorHandler);
        Configuration config = configurer.start();

        Optional<SequencedDeadLetterQueue<DeadLetter<EventMessage<?>>>> configuredDlq =
                config.eventProcessingConfiguration().deadLetterQueue(processingGroup);
        assertTrue(configuredDlq.isPresent());
        assertEquals(deadLetterQueue, configuredDlq.get());

        Optional<PooledStreamingEventProcessor> optionalProcessor =
                config.eventProcessingConfiguration()
                      .eventProcessor(processingGroup, PooledStreamingEventProcessor.class);
        assertTrue(optionalProcessor.isPresent());
        PooledStreamingEventProcessor resultProcessor = optionalProcessor.get();

        EventHandlerInvoker resultInvoker =
                getField(AbstractEventProcessor.class, "eventHandlerInvoker", resultProcessor);
        assertEquals(MultiEventHandlerInvoker.class, resultInvoker.getClass());

        MultiEventHandlerInvoker resultMultiInvoker = ((MultiEventHandlerInvoker) resultInvoker);
        List<EventHandlerInvoker> delegates = getField("delegates", resultMultiInvoker);
        assertFalse(delegates.isEmpty());
        DeadLetteringEventHandlerInvoker resultDeadLetteringInvoker =
                ((DeadLetteringEventHandlerInvoker) delegates.get(0));

        assertEquals(processingGroup, getField("processingGroup", resultDeadLetteringInvoker));
        assertTrue((Boolean) getField("allowReset", resultDeadLetteringInvoker));
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

    private void buildComplexEventHandlingConfiguration(CountDownLatch tokenStoreInvocation) {
        // Use InMemoryEventStorageEngine so tracking processors don't miss events
        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine());
        configurer.eventProcessing()
                  .registerSubscribingEventProcessor("subscribing")
                  .registerTrackingEventProcessor("tracking")
                  .assignHandlerInstancesMatching(
                          "subscribing", eh -> eh.getClass().isAssignableFrom(SubscribingEventHandler.class)
                  )
                  .assignHandlerInstancesMatching(
                          "tracking", eh -> eh.getClass().isAssignableFrom(TrackingEventHandler.class)
                  )
                  .registerEventHandler(c -> new SubscribingEventHandler())
                  .registerEventHandler(c -> new TrackingEventHandler())
                  .registerTokenStore("tracking", c -> new InMemoryTokenStore() {
                      @Override
                      public int[] fetchSegments(@Nonnull String processorName) {
                          tokenStoreInvocation.countDown();
                          return super.fetchSegments(processorName);
                      }
                  });
    }

    @SuppressWarnings("WeakerAccess")
    private static class StubEventProcessor implements EventProcessor {

        private final String name;
        private final EventHandlerInvoker eventHandlerInvoker;
        private final List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors = new ArrayList<>();

        public StubEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker) {
            this.name = name;
            this.eventHandlerInvoker = eventHandlerInvoker;
        }

        @Override
        public String getName() {
            return name;
        }

        public EventHandlerInvoker getEventHandlerInvoker() {
            return eventHandlerInvoker;
        }

        public List<?> getEventHandlers() {
            List<EventHandlerInvoker> invokers = ((MultiEventHandlerInvoker) getEventHandlerInvoker()).delegates();
            return ((SimpleEventHandlerInvoker) invokers.get(0))
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
        public Registration registerHandlerInterceptor(
                @Nonnull MessageHandlerInterceptor<? super EventMessage<?>> interceptor) {
            interceptors.add(interceptor);
            return () -> interceptors.remove(interceptor);
        }

        @Override
        public List<MessageHandlerInterceptor<? super EventMessage<?>>> getHandlerInterceptors() {
            return interceptors;
        }

        @Override
        public void start() {
            // noop
        }

        @Override
        public void shutDown() {
            // noop
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public boolean isError() {
            return false;
        }
    }

    @SuppressWarnings("WeakerAccess")
    @ProcessingGroup("processingGroup")
    public static class AnnotatedBean {

    }

    @SuppressWarnings("WeakerAccess")
    public static class AnnotatedBeanSubclass extends AnnotatedBean {

    }

    private static class StubInterceptor implements MessageHandlerInterceptor<EventMessage<?>> {
        @Override
        public Object handle(@Nonnull UnitOfWork<? extends EventMessage<?>> unitOfWork,
                             @Nonnull InterceptorChain interceptorChain)
                throws Exception {
            return interceptorChain.proceed();
        }
    }

    @SuppressWarnings("unused")
    @ProcessingGroup("subscribing")
    private static class SubscribingEventHandler {

        @EventHandler
        public void handle(Integer event, UnitOfWork<?> unitOfWork) {
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
        public void handle(Integer event, UnitOfWork<?> unitOfWork) {
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
        public void onError(@Nonnull Exception exception, @Nonnull EventMessage<?> event,
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
