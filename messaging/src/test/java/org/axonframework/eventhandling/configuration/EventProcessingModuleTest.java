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

package org.axonframework.eventhandling.configuration;

import org.axonframework.common.transaction.NoOpTransactionManager;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.configuration.Module;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.utils.AsyncInMemoryStreamableEventSource;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class EventProcessingModuleTest {

    @Test
    void subscribingProcessorIsRegisteredAndStarted() {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean stopped = new AtomicBoolean(false);

        SimpleEventHandlingComponent eventHandlingComponent = new SimpleEventHandlingComponent();
        SubscribableMessageSource<EventMessage<?>> messageSource = handler -> {
            started.set(true);
            return () -> stopped.getAndSet(true);
        };

        // todo: remove named
        var module = EventProcessingModule.subscribing("test-processor")
                                          .eventHandlingComponent(c -> eventHandlingComponent)
                                          .messageSource(c -> messageSource)
                                          .build();

        var configuration = MessagingConfigurer.create()
                                               .componentRegistry(cr -> cr.registerModule((Module) module))
                                               .build();

        configuration.start();
        assertThat(started).as("processor started").isTrue();
        configuration.shutdown();
        assertThat(stopped).as("processor stopped").isTrue();
    }

    @Test
    void streamingProcessorIsRegisteredAndStarted() {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean stopped = new AtomicBoolean(false);
        SimpleEventHandlingComponent eventHandlingComponent = new SimpleEventHandlingComponent();
        InMemoryTokenStore tokenStore = new InMemoryTokenStore();
        AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
        eventSource.setOnOpen(() -> started.set(true));
        eventSource.setOnClose(() -> stopped.set(true));

        var module = EventProcessingModule.pooledStreaming("test-processor")
                                          .eventHandlingComponent(c -> eventHandlingComponent)
                                          .tokenStore(c -> tokenStore)
                                          .eventSource(c -> eventSource)
                                          .workerExecutor(c -> Executors.newScheduledThreadPool(5))
                                          .coordinatorExecutor(c -> Executors.newScheduledThreadPool(1))
                                          .transactionManager(c -> new NoOpTransactionManager())
                                          .build();

        var configuration = MessagingConfigurer.create()
                                               .componentRegistry(cr -> cr.registerModule(module))
                                               .build();

        configuration.start();
        await().atMost(Duration.ofSeconds(1))
               .untilAsserted(() -> assertThat(started).as("processor started").isTrue());
        configuration.shutdown();
        await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(stopped).as("processor stopped").isTrue());
    }

    @Test
    void missingNameThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                EventProcessingModule.subscribing("")
                                     .eventHandlingComponent(c -> null)
                                     .messageSource(c -> null)
                                     .build()
        );
        assertTrue(ex.getMessage().contains("Processor name"));
    }

    @Test
    void missingEventHandlingComponentThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                EventProcessingModule.subscribing("test")
                                     .messageSource(c -> null)
                                     .build()
        );
        assertTrue(ex.getMessage().contains("EventHandlingComponent"));
    }

    @Test
    void missingMessageSourceThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                EventProcessingModule.subscribing("test")
                                     .eventHandlingComponent(c -> null)
                                     .build()
        );
        assertTrue(ex.getMessage().contains("MessageSource"));
    }

    @Test
    void missingTokenStoreThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                EventProcessingModule.pooledStreaming("test")
                                     .eventHandlingComponent(c -> null)
                                     .build()
        );
        assertTrue(ex.getMessage().contains("TokenStore"));
    }
} 