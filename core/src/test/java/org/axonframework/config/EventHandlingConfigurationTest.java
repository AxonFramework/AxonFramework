/*
 * Copyright (c) 2010-2016. Axon Framework
 *
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
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class EventHandlingConfigurationTest {

    private Configuration configuration;

    @Before
    public void setUp() throws Exception {
        configuration = mock(Configuration.class);
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

        module.assignHandlersMatching("java.util.concurrent", "concurrent"::equals);
        module.registerEventHandler(c -> new Object()); // --> java.lang
        module.registerEventHandler(c -> ""); // --> java.lang
        module.registerEventHandler(c -> "concurrent"); // --> java.util.concurrent
        module.registerEventHandler(c -> map); // --> java.util.concurrent
        module.registerEventHandler(c -> annotatedBean);
        module.initialize(configuration);

        assertEquals(3, processors.size());
        assertTrue(processors.get("java.util.concurrent").getEventHandlers().contains("concurrent"));
        assertTrue(processors.get("java.util.concurrent").getEventHandlers().contains(map));
        assertTrue(processors.get("java.lang").getEventHandlers().contains(""));
        assertTrue(processors.get("processingGroup").getEventHandlers().contains(annotatedBean));
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
        module.initialize(configuration);

        assertEquals(3, processors.size());
        assertTrue(processors.get("java.util.concurrent2").getEventHandlers().contains("concurrent"));
        assertTrue(processors.get("java.util.concurrent").getEventHandlers().contains(map));
        assertTrue(processors.get("java.lang").getEventHandlers().contains(""));
    }

    private static class StubEventProcessor implements EventProcessor {

        private final String name;
        private final List<?> eventHandlers;

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
        public Registration registerInterceptor(MessageHandlerInterceptor<EventMessage<?>> interceptor) {
            return () -> true;
        }

        @Override
        public void start() {

        }

        @Override
        public void shutDown() {

        }
    }

    @ProcessingGroup("processingGroup")
    public static class AnnotatedBean {
    }
}
