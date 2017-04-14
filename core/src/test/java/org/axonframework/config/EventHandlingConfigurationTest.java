/*
 * Copyright (c) 2010-2017. Axon Framework
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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EventHandlingConfigurationTest {

    private Configurer configurer;

    @Before
    public void setUp() throws Exception {
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
        assertEquals(1, config.getModules().size());
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
        assertTrue(processors.get("java.util.concurrent2").getInterceptors().get(0) instanceof CorrelationDataInterceptor);
        assertTrue(processors.get("java.util.concurrent").getEventHandlers().contains(map));
        assertTrue(processors.get("java.util.concurrent").getInterceptors().get(0) instanceof CorrelationDataInterceptor);
        assertTrue(processors.get("java.lang").getEventHandlers().contains(""));
        assertTrue(processors.get("java.lang").getInterceptors().get(0) instanceof CorrelationDataInterceptor);
        assertEquals(1, config.getModules().size());
    }

    @Test
    public void testAssignInterceptors() {
        EventHandlingConfiguration module = new EventHandlingConfiguration()
                .usingTrackingProcessors()
                .registerEventProcessor("default", (config, name, handlers) -> {
                    StubEventProcessor processor = new StubEventProcessor(name, handlers);
                    return processor;
                });
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
        assertEquals(3, ((StubEventProcessor)module.getProcessor("default").get()).getInterceptors().size());
        assertEquals(1, config.getModules().size());
    }

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
        public void start() {

        }

        @Override
        public void shutDown() {

        }

        public List<MessageHandlerInterceptor<? super EventMessage<?>>> getInterceptors() {
            return interceptors;
        }
    }

    @ProcessingGroup("processingGroup")
    public static class AnnotatedBean {
    }

    public static class AnnotatedBeanSubclass extends AnnotatedBean {

    }

    private static class StubInterceptor implements MessageHandlerInterceptor<EventMessage<?>> {
        @Override
        public Object handle(UnitOfWork<? extends EventMessage<?>> unitOfWork, InterceptorChain interceptorChain) throws Exception {
            return interceptorChain.proceed();
        }
    }
}
