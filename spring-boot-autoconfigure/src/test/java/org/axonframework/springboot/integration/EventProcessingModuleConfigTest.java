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

package org.axonframework.springboot.integration;

import org.axonframework.config.EventProcessingModule;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.messaging.interceptors.LoggingInterceptor;
import org.axonframework.spring.stereotype.Saga;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests configuration of {@link EventProcessingModule}.
 *
 * @author Milan Savic
 */
class EventProcessingModuleConfigTest {

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner().withPropertyValues("axon.axonserver.enabled:false")
                                                               .withUserConfiguration(TestContext.class);
    }

    @Test
    void eventProcessingConfiguration() {
        testApplicationContext.run(context -> {
            EventProcessingModule eventProcessingConfiguration = context.getBean(EventProcessingModule.class);
            assertEquals(3, eventProcessingConfiguration.eventProcessors().size());
            assertTrue(eventProcessingConfiguration.eventProcessor("processor2").isPresent());
            assertTrue(eventProcessingConfiguration.eventProcessor("subscribingProcessor").isPresent());

            Optional<EventProcessor> optionalProcessorOne =
                    eventProcessingConfiguration.eventProcessorByProcessingGroup("processor1");
            assertTrue(optionalProcessorOne.isPresent());
            EventProcessor processorOne = optionalProcessorOne.get();
            assertEquals("processor2", processorOne.getName());
            List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptorsFor =
                    eventProcessingConfiguration.interceptorsFor("processor2");
            // Although we only add two Interceptors,
            //  the count is three due to an Interceptor set by the constructor of the TrackingEventProcessor.
            assertEquals(3, interceptorsFor.size());
            assertTrue(interceptorsFor.stream().anyMatch(i -> i instanceof CorrelationDataInterceptor));
            assertTrue(interceptorsFor.stream().anyMatch(i -> i instanceof LoggingInterceptor));

            Optional<EventProcessor> optionalProcessorTwo =
                    eventProcessingConfiguration.eventProcessorByProcessingGroup("processor2");
            assertTrue(optionalProcessorTwo.isPresent());
            assertEquals("processor2", optionalProcessorTwo.get().getName());

            Optional<EventProcessor> optionalProcessorThree =
                    eventProcessingConfiguration.eventProcessorByProcessingGroup("processor3");
            assertTrue(optionalProcessorThree.isPresent());
            assertEquals("subscribingProcessor", optionalProcessorThree.get().getName());
            optionalProcessorThree = eventProcessingConfiguration.eventProcessorByProcessingGroup("Saga3Processor");
            assertTrue(optionalProcessorThree.isPresent());
            assertEquals("subscribingProcessor", optionalProcessorThree.get().getName());

            Optional<EventProcessor> optionalProcessorFour =
                    eventProcessingConfiguration.eventProcessorByProcessingGroup("processor4");
            assertTrue(optionalProcessorFour.isPresent());
            assertEquals("processor4", optionalProcessorFour.get().getName());
        });
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestContext {

        @Bean
        public EventProcessingModule eventProcessingConfiguration() {
            EventProcessingModule config = new EventProcessingModule();
            config.assignProcessingGroup("processor1", "processor2");
            config.assignProcessingGroup(group -> group.contains("3") ? "subscribingProcessor" : group);
            config.registerSubscribingEventProcessor("subscribingProcessor");
            config.registerDefaultHandlerInterceptor((configuration, name) -> new LoggingInterceptor<>());
            return config;
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

        @Saga
        @ProcessingGroup("processor4")
        public static class Saga4 {

        }

        @Saga
        @ProcessingGroup("processor4")
        public static class Saga5 {

        }
    }
}
