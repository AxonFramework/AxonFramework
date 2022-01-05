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

package org.axonframework.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedType;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.upcasting.event.ContextAwareEventMultiUpcaster;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import java.beans.ConstructorProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.axonframework.springboot.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the {@link PooledStreamingEventProcessor}. Tests, for example, that all events from an {@link
 * org.axonframework.serialization.upcasting.event.EventMultiUpcaster} are read.
 *
 * @author Steven van Beelen
 */
class PooledStreamingEventProcessorIntegrationTest {

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner().withPropertyValues("axon.axonserver.enabled:false")
                                                               .withUserConfiguration(Context.class);
    }

    @Test
    void testAllEventsFromMultiUpcasterAreHandled() {
        testApplicationContext.run(context -> {
            EventProcessingConfiguration processingConfig = context.getBean(EventProcessingConfiguration.class);
            Optional<PooledStreamingEventProcessor> optionalProcessor =
                    processingConfig.eventProcessor("upcaster-test", PooledStreamingEventProcessor.class);

            assertTrue(optionalProcessor.isPresent());
            PooledStreamingEventProcessor processor = optionalProcessor.get();
            processor.shutDown();

            EventGateway eventGateway = context.getBean(EventGateway.class);
            eventGateway.publish(new OriginalEvent("my-text"));
            processor.start();
            assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, processor.processingStatus().size()));

            EventHandlingComponent eventHandlingComponent =
                    context.getBean(EventHandlingComponent.class);

            assertNotNull(eventHandlingComponent);
            assertWithin(500, TimeUnit.MILLISECONDS,
                         () -> assertEquals(0, eventHandlingComponent.getOriginalEventCounter().get()));
            assertWithin(500, TimeUnit.MILLISECONDS,
                         () -> assertEquals(2, eventHandlingComponent.getUpcastedEventCounter().get()));
        });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class Context {

        @Bean
        @Qualifier("eventSerializer")
        public Serializer eventSerializer() {
            return JacksonSerializer.defaultSerializer();
        }

        @Bean
        public ConfigurerModule defaultToPooledStreaming() {
            return configurer -> configurer.eventProcessing()
                                           .usingPooledStreamingEventProcessors()
                                           .registerPooledStreamingEventProcessorConfiguration(
                                                   (config, builder) -> builder.initialSegmentCount(1)
                                           );
        }

        @Bean
        public MultiUpcaster multiUpcaster() {
            return new MultiUpcaster();
        }

        @Bean
        public EventHandlingComponent eventHandlingComponent() {
            return new EventHandlingComponent();
        }
    }

    private static class MultiUpcaster extends ContextAwareEventMultiUpcaster<Map<Object, Object>> {

        @Override
        protected boolean canUpcast(IntermediateEventRepresentation intermediateRepresentation,
                                    Map<Object, Object> context) {
            return intermediateRepresentation.getType().getName().equals(OriginalEvent.class.getName());
        }

        @Override
        protected Stream<IntermediateEventRepresentation> doUpcast(
                IntermediateEventRepresentation intermediateRepresentation,
                Map<Object, Object> context
        ) {
            return Stream.of(
                    intermediateRepresentation.upcastPayload(
                            new SimpleSerializedType(UpcastedEvent.class.getName(), null),
                            JsonNode.class,
                            // We keep the event the same, only change the type
                            node -> node
                    ),
                    intermediateRepresentation.upcastPayload(
                            new SimpleSerializedType(UpcastedEvent.class.getName(), null),
                            JsonNode.class,
                            // We keep the event the same, only change the type
                            node -> node
                    )
            );
        }

        @Override
        protected Map<Object, Object> buildContext() {
            return new HashMap<>();
        }
    }

    @ProcessingGroup("upcaster-test")
    private static class EventHandlingComponent {

        private final AtomicInteger originalEventCounter = new AtomicInteger(0);
        private final AtomicInteger upcastedEventCounter = new AtomicInteger(0);

        @EventHandler
        public void on(@SuppressWarnings("unused") OriginalEvent event) {
            originalEventCounter.incrementAndGet();
        }

        @EventHandler
        public void on(@SuppressWarnings("unused") UpcastedEvent event) {
            upcastedEventCounter.incrementAndGet();
        }

        public AtomicInteger getOriginalEventCounter() {
            return originalEventCounter;
        }

        public AtomicInteger getUpcastedEventCounter() {
            return upcastedEventCounter;
        }
    }

    @SuppressWarnings("unused")
    private static class OriginalEvent {

        private final String textField;

        @ConstructorProperties({"textField"})
        private OriginalEvent(String textField) {
            this.textField = textField;
        }

        public String getTextField() {
            return textField;
        }
    }

    @SuppressWarnings("unused")
    private static class UpcastedEvent {

        private final String textField;

        @ConstructorProperties({"textField"})
        private UpcastedEvent(String textField) {
            this.textField = textField;
        }

        public String getTextField() {
            return textField;
        }
    }
}
