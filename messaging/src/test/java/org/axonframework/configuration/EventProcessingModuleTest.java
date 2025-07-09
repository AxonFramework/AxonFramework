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

package org.axonframework.configuration;

import org.axonframework.common.transaction.NoOpTransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.utils.AsyncInMemoryStreamableEventSource;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class EventProcessingModuleTest {

    private MessagingConfigurer configurer;

    @BeforeEach
    void setUp() {
        configurer = MessagingConfigurer.create();
    }

    @Test
    void subscribingProcessorIsRegisteredAndStarted() {
        // given
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean stopped = new AtomicBoolean(false);
        SimpleEventHandlingComponent eventHandlingComponent = new SimpleEventHandlingComponent();
        SubscribableMessageSource<EventMessage<?>> messageSource = handler -> {
            started.set(true);
            return () -> stopped.getAndSet(true);
        };

        EventProcessingModule module = EventProcessingModule.named("test-subscriber")
                                                            .subscribing()
                                                            .eventHandlingComponent(c -> eventHandlingComponent)
                                                            .messageSource(c -> messageSource)
                                                            .build();

        var configuration = configurer
                .componentRegistry(cr -> cr.registerModule(module))
                .build();

        // when
        configuration.start();

        // then
        assertTrue(started.get(), "Processor should be started");

        // when
        configuration.shutdown();

        // then
        assertTrue(stopped.get(), "Processor should be stopped");
    }

    @Test
    void streamingProcessorIsRegisteredAndStarted() {
        // given
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean stopped = new AtomicBoolean(false);
        SimpleEventHandlingComponent eventHandlingComponent = new SimpleEventHandlingComponent();
        InMemoryTokenStore tokenStore = new InMemoryTokenStore();
        AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
        eventSource.setOnOpen(() -> started.set(true));
        eventSource.setOnClose(() -> stopped.set(true));

        EventProcessingModule module = EventProcessingModule.named("streaming-test")
                .streaming()
                .eventHandlingComponent(c -> eventHandlingComponent)
                .tokenStore(c -> tokenStore)
                .eventSource(c -> eventSource)
                .transactionManager(c -> new NoOpTransactionManager())
                .build();

        var configuration = MessagingConfigurer.create()
                .componentRegistry(cr -> cr.registerModule(module))
                .build();

        configuration.start();

        // Awaitility: Wait for the processor to start (async startup)
        await().atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(started).as("processor started").isTrue()
        );

        configuration.shutdown();

        // Awaitility: Wait for the processor to stop (async shutdown)
        await().atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(stopped).as("processor stopped").isTrue()
        );
    }

    @Test
    void missingNameThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                EventProcessingModule.named("")
                                     .subscribing()
                                     .eventHandlingComponent(c -> null)
                                     .messageSource(c -> null)
                                     .build()
        );
        assertTrue(ex.getMessage().contains("Processor name"));
    }

    @Test
    void missingEventHandlingComponentThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                EventProcessingModule.named("test")
                                     .subscribing()
                                     .messageSource(c -> null)
                                     .build()
        );
        assertTrue(ex.getMessage().contains("EventHandlingComponent"));
    }

    @Test
    void missingMessageSourceThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                EventProcessingModule.named("test")
                                     .subscribing()
                                     .eventHandlingComponent(c -> null)
                                     .build()
        );
        assertTrue(ex.getMessage().contains("MessageSource"));
    }

    @Test
    void missingTokenStoreThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                EventProcessingModule.named("test")
                                     .streaming()
                                     .eventHandlingComponent(c -> null)
                                     .build()
        );
        assertTrue(ex.getMessage().contains("TokenStore"));
    }
} 