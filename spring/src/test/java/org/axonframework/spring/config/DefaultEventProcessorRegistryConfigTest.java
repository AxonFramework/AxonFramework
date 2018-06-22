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

import org.axonframework.config.EventProcessorRegistry;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.spring.stereotype.Saga;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

/**
 * Tests configuration of {@link org.axonframework.config.DefaultEventProcessorRegistry}.
 *
 * @author Milan Savic
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class DefaultEventProcessorRegistryConfigTest {

    @Autowired
    private EventProcessorRegistry registry;

    @Test
    public void testProcessorConfiguration() {
        assertEquals(2, registry.eventProcessors().size());
        assertTrue(registry.eventProcessor("processor2").isPresent());
        assertTrue(registry.eventProcessor("subscribingProcessor").isPresent());

        assertEquals("processor2", registry.eventProcessorByProcessingGroup("processor1").get().getName());
        assertEquals("processor2", registry.eventProcessorByProcessingGroup("processor2").get().getName());
        assertEquals("subscribingProcessor", registry.eventProcessorByProcessingGroup("processor3").get().getName());
        assertEquals("subscribingProcessor",
                     registry.eventProcessorByProcessingGroup("Saga3Processor").get().getName());
    }

    @EnableAxon
    @Configuration
    public static class Context {

        @Autowired
        public void configure(EventProcessorRegistry registry) {
            registry.usingTrackingProcessors();
            registry.assignProcessingGroup("processor1", "processor2");
            registry.assignProcessingGroup(group -> group.contains("3") ? "subscribingProcessor" : "processor2");
            registry.registerSubscribingEventProcessor("subscribingProcessor");
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
