/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.*;
import org.axonframework.eventhandling.async.FullConcurrencyPolicy;
import org.axonframework.eventhandling.async.SequentialPolicy;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.axonframework.common.ReflectionUtils.getFieldValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EventProcessingModuleTest {

    private Configurer configurer;

    @Before
    public void setUp() {
        configurer = DefaultConfigurer.defaultConfiguration();
    }

    @Test
    public void testAssignmentRules() {
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
    public void testProcessorsDefaultToSubscribingWhenUsingSimpleEventBus() {
        Configuration configuration = DefaultConfigurer.defaultConfiguration()
                                                       .configureEventBus(c -> SimpleEventBus.builder().build())
                                                       .eventProcessing(ep -> ep.registerEventHandler(c -> new SubscribingEventHandler())
                                                                                .registerEventHandler(c -> new TrackingEventHandler()))
                                                       .start();

        assertTrue(configuration.eventProcessingConfiguration().eventProcessor("subscribing").isPresent());
        assertTrue(configuration.eventProcessingConfiguration().eventProcessor("subscribing").map(p -> p instanceof SubscribingEventProcessor).orElse(false));
        assertTrue(configuration.eventProcessingConfiguration().eventProcessor("tracking").isPresent());
        assertTrue(configuration.eventProcessingConfiguration().eventProcessor("tracking").map(p -> p instanceof SubscribingEventProcessor).orElse(false));
    }

    @Test(expected = AxonConfigurationException.class)
    public void testAssigningATrackingProcessorFailsWhenUsingSimpleEventBus() {
        DefaultConfigurer.defaultConfiguration()
                         .configureEventBus(c -> SimpleEventBus.builder().build())
                         .eventProcessing(ep -> ep.registerEventHandler(c -> new SubscribingEventHandler())
                                                  .registerEventHandler(c -> new TrackingEventHandler())
                                                  .registerTrackingEventProcessor("tracking"))
                         .start();
    }

    @Test
    public void testAssignmentRulesOverrideThoseWithLowerPriority() {
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
    public void testDefaultAssignToKeepsAnnotationScanning() {
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
    public void testTypeAssignment() {
        configurer.eventProcessing()
                  .assignHandlerTypesMatching("myGroup", c -> "java.lang".equals(c.getPackage().getName()))
                  .registerSaga(Object.class)
                  .registerSaga(ConcurrentMap.class)
                  .registerSaga(String.class)
                  .registerEventHandler(c -> new HashMap<>());
        EventProcessingConfiguration configuration = configurer.start()
                                                               .eventProcessingConfiguration();

        assertEquals(3, configuration.eventProcessors().size());
        assertTrue(configuration.eventProcessor("myGroup").isPresent());
        assertTrue(configuration.eventProcessor("java.util").isPresent());
        assertTrue(configuration.eventProcessor("ConcurrentMapProcessor").isPresent());
    }

    @Test
    public void testAssignSequencingPolicy() throws NoSuchFieldException {
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

        AbstractEventProcessor defaultProcessor =
                config.eventProcessingConfiguration().eventProcessor("default", AbstractEventProcessor.class).get();
        AbstractEventProcessor specialProcessor =
                config.eventProcessingConfiguration().eventProcessor("special", AbstractEventProcessor.class).get();

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
    public void testAssignInterceptors() {
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
        assertEquals(3,
                     config.eventProcessingConfiguration().eventProcessor("default").get()
                           .getHandlerInterceptors().size());
    }

    @Test
    public void testConfigureMonitor() throws Exception {
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
    public void testConfigureDefaultListenerInvocationErrorHandler() throws Exception {
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
    public void testConfigureListenerInvocationErrorHandlerPerEventProcessor() throws Exception {
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
    public void testConfigureDefaultErrorHandler() throws Exception {
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
    public void testConfigureErrorHandlerPerEventProcessor() throws Exception {
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

    private void buildComplexEventHandlingConfiguration(CountDownLatch tokenStoreInvocation) {
        // Use InMemoryEventStorageEngine so tracking processors don't miss events
        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine());
        configurer.eventProcessing()
                  .registerSubscribingEventProcessor("subscribing")
                  .registerTrackingEventProcessor("tracking")
                  .assignHandlerInstancesMatching("subscribing",
                                                  eh -> eh.getClass().isAssignableFrom(SubscribingEventHandler.class))
                  .assignHandlerInstancesMatching("tracking", eh -> eh.getClass().isAssignableFrom(TrackingEventHandler.class))
                  .registerEventHandler(c -> new SubscribingEventHandler())
                  .registerEventHandler(c -> new TrackingEventHandler())
                  .registerTokenStore("tracking", c -> new InMemoryTokenStore() {
                      @Override
                      public int[] fetchSegments(String processorName) {
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
            return ((SimpleEventHandlerInvoker) ((MultiEventHandlerInvoker) getEventHandlerInvoker())
                    .delegates()
                    .get(0))
                    .eventHandlers();
        }

        @Override
        public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super EventMessage<?>> interceptor) {
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
        public Object handle(UnitOfWork<? extends EventMessage<?>> unitOfWork, InterceptorChain interceptorChain)
                throws Exception {
            return interceptorChain.proceed();
        }
    }

    @SuppressWarnings("unused")
    @ProcessingGroup("subscribing")
    private class SubscribingEventHandler {

        @EventHandler
        public void handle(Integer event, UnitOfWork unitOfWork) {
            throw new IllegalStateException();
        }

        @EventHandler
        public void handle(Boolean event) {
            throw new IllegalStateException();
        }
    }

    @SuppressWarnings("unused")
    @ProcessingGroup("tracking")
    private class TrackingEventHandler {

        @EventHandler
        public void handle(String event) {

        }

        @EventHandler
        public void handle(Integer event, UnitOfWork unitOfWork) {
            throw new IllegalStateException();
        }

        @EventHandler
        public void handle(Boolean event) {
            throw new IllegalStateException();
        }
    }

    private class StubErrorHandler implements ErrorHandler, ListenerInvocationErrorHandler {

        private final CountDownLatch latch;
        private long errorCounter = 0;

        private StubErrorHandler(int count) {
            this.latch = new CountDownLatch(count);
        }

        @Override
        public void handleError(ErrorContext errorContext) {
            errorCounter++;
            latch.countDown();
        }

        @Override
        public void onError(Exception exception, EventMessage<?> event, EventMessageHandler eventHandler) {
            errorCounter++;
            latch.countDown();
        }

        @SuppressWarnings("WeakerAccess")
        public long getErrorCounter() {
            return errorCounter;
        }

        public boolean await(long timeout, TimeUnit timeUnit) throws InterruptedException {
            return latch.await(timeout, timeUnit);
        }
    }
}
