/*
 * Copyright (c) 2010-2018. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.config;

import org.axonframework.common.Registration;
import org.axonframework.eventhandling.AbstractEventProcessor;
import org.axonframework.eventhandling.ErrorContext;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.MultiEventHandlerInvoker;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.async.FullConcurrencyPolicy;
import org.axonframework.eventhandling.async.SequentialPolicy;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.axonframework.common.ReflectionUtils.getFieldValue;
import static org.axonframework.config.ConfigAssertions.assertExpectedModules;
import static org.junit.Assert.*;

public class EventHandlingConfigurationTest {

    private Configurer configurer;

    @Before
    public void setUp() {
        configurer = DefaultConfigurer.defaultConfiguration();
    }

    @Test
    public void testAssignmentRules() {
        Map<String, StubEventProcessor> processors = new HashMap<>();
        EventHandlingConfiguration module = new EventHandlingConfiguration()
                .registerEventProcessorFactory((config, name, handlers) -> {
                    StubEventProcessor processor = new StubEventProcessor(name, handlers);
                    processors.put(name, processor);
                    return processor;
                });
        ConcurrentHashMap<Object, Object> map = new ConcurrentHashMap<>();
        AnnotatedBean annotatedBean = new AnnotatedBean();
        AnnotatedBeanSubclass annotatedBeanSubclass = new AnnotatedBeanSubclass();

        module.assignHandlersMatching("java.util.concurrent", "concurrent"::equals);
        module.registerEventHandler(c -> new Object()); // --> java.lang
        module.registerEventHandler(c -> ""); // --> java.lang
        module.registerEventHandler(c -> "concurrent"); // --> java.util.concurrent
        module.registerEventHandler(c -> map); // --> java.util.concurrent
        module.registerEventHandler(c -> annotatedBean);
        module.registerEventHandler(c -> annotatedBeanSubclass);
        configurer.registerModule(module);
        Configuration config = configurer.start();

        assertEquals(3, processors.size());
        assertTrue(processors.get("java.util.concurrent").getEventHandlers().contains("concurrent"));
        assertTrue(processors.get("java.util.concurrent").getEventHandlers().contains(map));
        assertTrue(processors.get("java.lang").getEventHandlers().contains(""));
        assertTrue(processors.get("processingGroup").getEventHandlers().contains(annotatedBean));
        assertTrue(processors.get("processingGroup").getEventHandlers().contains(annotatedBeanSubclass));
        assertEquals(2, config.getModules().size());
        assertExpectedModules(config,
                              EventProcessingConfiguration.class,
                              EventHandlingConfiguration.class);
    }

    @Test
    public void testAssignmentRulesOverrideThoseWithLowerPriority() {
        Map<String, StubEventProcessor> processors = new HashMap<>();
        EventHandlingConfiguration module = new EventHandlingConfiguration()
                .registerEventProcessorFactory((config, name, handlers) -> {
                    StubEventProcessor processor = new StubEventProcessor(name, handlers);
                    processors.put(name, processor);
                    return processor;
                });
        ConcurrentHashMap<Object, Object> map = new ConcurrentHashMap<>();

        module.assignHandlersMatching("java.util.concurrent", "concurrent"::equals);
        module.assignHandlersMatching("java.util.concurrent2", 1, "concurrent"::equals);
        module.registerEventHandler(c -> new Object()); // --> java.lang
        module.registerEventHandler(c -> ""); // --> java.lang
        module.registerEventHandler(c -> "concurrent"); // --> java.util.concurrent2
        module.registerEventHandler(c -> map); // --> java.util.concurrent
        configurer.registerModule(module);
        Configuration config = configurer.start();

        assertEquals(3, processors.size());
        assertTrue(processors.get("java.util.concurrent2").getEventHandlers().contains("concurrent"));
        assertTrue(processors.get("java.util.concurrent2").getHandlerInterceptors().iterator()
                             .next() instanceof CorrelationDataInterceptor);
        assertTrue(processors.get("java.util.concurrent").getEventHandlers().contains(map));
        assertTrue(processors.get("java.util.concurrent").getHandlerInterceptors().iterator()
                             .next() instanceof CorrelationDataInterceptor);
        assertTrue(processors.get("java.lang").getEventHandlers().contains(""));
        assertTrue(processors.get("java.lang").getHandlerInterceptors().iterator()
                             .next() instanceof CorrelationDataInterceptor);
        assertEquals(2, config.getModules().size());
        assertExpectedModules(config,
                              EventProcessingConfiguration.class,
                              EventHandlingConfiguration.class);
    }

    @Test
    public void testDefaultAssignToKeepsAnnotationScanning() {
        Map<String, StubEventProcessor> processors = new HashMap<>();
        EventHandlingConfiguration module = new EventHandlingConfiguration()
                .registerEventProcessorFactory((config, name, handlers) -> {
                    StubEventProcessor processor = new StubEventProcessor(name, handlers);
                    processors.put(name, processor);
                    return processor;
                });
        AnnotatedBean annotatedBean = new AnnotatedBean();
        Object object = new Object();

        module.assignHandlersMatching("java.util.concurrent", "concurrent"::equals);
        module.byDefaultAssignTo("default");
        module.registerEventHandler(c -> object);        // --> default
        module.registerEventHandler(c -> "concurrent");  // --> java.util.concurrent
        module.registerEventHandler(c -> annotatedBean); // --> processingGroup
        configurer.registerModule(module);
        Configuration config = configurer.start();

        assertEquals(3, processors.size());
        assertTrue(processors.get("default").getEventHandlers().contains(object));
        assertTrue(processors.get("java.util.concurrent").getEventHandlers().contains("concurrent"));
        assertTrue(processors.get("processingGroup").getEventHandlers().contains(annotatedBean));
        assertEquals(2, config.getModules().size());
        assertExpectedModules(config,
                              EventProcessingConfiguration.class,
                              EventHandlingConfiguration.class);
    }

    @Test
    public void testAssignSequencingPolicy() throws NoSuchFieldException {
        Object mockHandler = new Object();
        Object specialHandler = new Object();
        SequentialPolicy sequentialPolicy = new SequentialPolicy();
        FullConcurrencyPolicy fullConcurrencyPolicy = new FullConcurrencyPolicy();
        EventHandlingConfiguration module = new EventHandlingConfiguration()
                .registerEventHandler(c -> mockHandler)
                .registerEventHandler(c -> specialHandler)
                .assignHandlersMatching("special", specialHandler::equals)
                .byDefaultAssignTo("default")
                .registerDefaultSequencingPolicy(c -> sequentialPolicy)
                .registerSequencingPolicy("special", c -> fullConcurrencyPolicy);
        configurer.registerModule(module);
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
        EventHandlingConfiguration module = new EventHandlingConfiguration()
                .usingTrackingProcessors()
                .registerEventProcessor("default", (config, name, handlers) -> new StubEventProcessor(name, handlers));
        module.byDefaultAssignTo("default");
        module.assignHandlersMatching("concurrent", 1, "concurrent"::equals);
        module.registerEventHandler(c -> new Object()); // --> java.lang
        module.registerEventHandler(c -> "concurrent"); // --> java.util.concurrent2

        StubInterceptor interceptor1 = new StubInterceptor();
        StubInterceptor interceptor2 = new StubInterceptor();
        module.registerHandlerInterceptor("default", c -> interceptor1);
        module.registerHandlerInterceptor((c, n) -> interceptor2);
        configurer.registerModule(module);
        Configuration config = configurer.start();

        // CorrelationDataInterceptor is automatically configured
        assertEquals(3, ((StubEventProcessor) module.getProcessor("default").get()).getHandlerInterceptors().size());
        assertEquals(2, config.getModules().size());
        assertExpectedModules(config,
                              EventHandlingConfiguration.class,
                              EventProcessingConfiguration.class);
    }

    @Test
    public void testConfigureMonitor() throws Exception {
        MessageCollectingMonitor subscribingMonitor = new MessageCollectingMonitor();
        MessageCollectingMonitor trackingMonitor = new MessageCollectingMonitor(1);
        CountDownLatch tokenStoreInvocation = new CountDownLatch(1);

        EventHandlingConfiguration module = buildComplexEventHandlingConfiguration(tokenStoreInvocation)
                .configureMessageMonitor("subscribing", c -> subscribingMonitor)
                .configureMessageMonitor("tracking", c -> trackingMonitor);
        configurer.registerModule(module);
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

        EventHandlingConfiguration module = buildComplexEventHandlingConfiguration(tokenStoreInvocation)
                .configureListenerInvocationErrorHandler(config -> errorHandler);
        configurer.registerModule(module);
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

        EventHandlingConfiguration module = buildComplexEventHandlingConfiguration(tokenStoreInvocation)
                .configureListenerInvocationErrorHandler("subscribing", config -> subscribingErrorHandler)
                .configureListenerInvocationErrorHandler("tracking", config -> trackingErrorHandler);
        configurer.registerModule(module);
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

        EventHandlingConfiguration module = buildComplexEventHandlingConfiguration(tokenStoreInvocation)
                .configureErrorHandler(config -> errorHandler);
        configurer.registerModule(module);
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

        EventHandlingConfiguration module = buildComplexEventHandlingConfiguration(tokenStoreInvocation)
                .configureErrorHandler("subscribing", config -> subscribingErrorHandler)
                .configureErrorHandler("tracking", config -> trackingErrorHandler);
        configurer.registerModule(module);
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

    private EventHandlingConfiguration buildComplexEventHandlingConfiguration(CountDownLatch tokenStoreInvocation) {
        // Use InMemoryEventStorageEngine so tracking processors don't miss events
        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine());
        return new EventHandlingConfiguration()
                .registerSubscribingEventProcessor("subscribing")
                .registerTrackingProcessor("tracking")
                .assignHandlersMatching("subscribing",
                                        eh -> eh.getClass().isAssignableFrom(SubscribingEventHandler.class))
                .assignHandlersMatching("tracking", eh -> eh.getClass().isAssignableFrom(TrackingEventHandler.class))
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
        private final List<?> eventHandlers;
        private final List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors = new ArrayList<>();

        public StubEventProcessor(String name, List<?> eventHandlers) {
            this.name = name;
            this.eventHandlers = eventHandlers;
        }

        @Override
        public String getName() {
            return name;
        }

        public List<?> getEventHandlers() {
            return eventHandlers;
        }

        @Override
        public Registration registerInterceptor(MessageHandlerInterceptor<? super EventMessage<?>> interceptor) {
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
            unitOfWork.rollback(new IllegalStateException());
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
            unitOfWork.rollback(new IllegalStateException());
        }

        @EventHandler
        public void handle(Boolean event) {
            throw new IllegalStateException();
        }
    }

    private class StubErrorHandler implements ErrorHandler, ListenerInvocationErrorHandler {

        private long errorCounter = 0;
        private final CountDownLatch latch;

        private StubErrorHandler(int count) {
            this.latch = new CountDownLatch(count);
        }

        @Override
        public void handleError(ErrorContext errorContext) {
            errorCounter++;
            latch.countDown();
        }

        @Override
        public void onError(Exception exception, EventMessage<?> event, EventListener eventListener) {
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
