/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.spring.config;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.messaging.interceptors.LoggingInterceptor;
import org.axonframework.spring.stereotype.Saga;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests configuration of {@link EventProcessingConfiguration}.
 *
 * @author Milan Savic
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class EventProcessingConfigurationConfigTest {

    @Autowired
    private EventProcessingConfiguration eventProcessingConfiguration;

    @Test
    public void testEventProcessingConfiguration() {
        assertEquals(2, eventProcessingConfiguration.eventProcessors().size());
        assertTrue(eventProcessingConfiguration.eventProcessor("processor2").isPresent());
        assertTrue(eventProcessingConfiguration.eventProcessor("subscribingProcessor").isPresent());

        EventProcessor processor2 = eventProcessingConfiguration.eventProcessorByProcessingGroup("processor1").get();
        assertEquals("processor2", processor2.getName());
        List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptorsFor = eventProcessingConfiguration
                .interceptorsFor("processor2");
        assertEquals(2, interceptorsFor.size());
        assertTrue(interceptorsFor.stream().anyMatch(i -> i instanceof CorrelationDataInterceptor));
        assertTrue(interceptorsFor.stream().anyMatch(i -> i instanceof LoggingInterceptor));

        assertEquals("processor2",
                     eventProcessingConfiguration.eventProcessorByProcessingGroup("processor2").get().getName());
        assertEquals("subscribingProcessor",
                     eventProcessingConfiguration.eventProcessorByProcessingGroup("processor3").get().getName());
        assertEquals("subscribingProcessor",
                     eventProcessingConfiguration.eventProcessorByProcessingGroup("Saga3Processor").get().getName());
    }

    @EnableAxon
    @Configuration
    public static class Context {

        @Autowired
        public void configure(EventProcessingConfiguration config) {
            config.usingTrackingProcessors();
            config.assignProcessingGroup("processor1", "processor2");
            config.assignProcessingGroup(group -> group.contains("3") ? "subscribingProcessor" : "processor2");
            config.registerSubscribingEventProcessor("subscribingProcessor");
            config.registerHandlerInterceptor((configuration, name) -> new LoggingInterceptor<>());
        }

        @Saga
        @ProcessingGroup("processor1")
        public static class Saga1 {

        }

        @Saga
        @ProcessingGroup("processor2")
        public static class Saga2 {

        }

        @Saga
        public static class Saga3 {

        }

        @Component
        @ProcessingGroup("processor3")
        public static class EventHandler1 {

            @EventHandler
            public void on(String evt) {
                // nothing to do
            }
        }
    }
}
