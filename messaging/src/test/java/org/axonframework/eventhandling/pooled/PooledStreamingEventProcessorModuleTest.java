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

package org.axonframework.eventhandling.pooled;

import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.utils.AsyncInMemoryStreamableEventSource;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PooledStreamingEventProcessorModuleTest {

    @Test
    void registersWithLifecycleHooks() {
        // given
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean stopped = new AtomicBoolean(false);
        AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
        eventSource.setOnOpen(() -> started.set(true));
        eventSource.setOnClose(() -> stopped.set(true));

        var eventHandlingComponent1 = new SimpleEventHandlingComponent();
        eventHandlingComponent1.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());
        var eventHandlingComponent2 = new SimpleEventHandlingComponent();
        eventHandlingComponent2.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());
        EventProcessorModule module = EventProcessorModule
                .pooledStreaming("test-processor")
                .customize(cfg -> customization ->
                        customization
                                .eventHandlingComponents(List.of(eventHandlingComponent1, eventHandlingComponent2)) // todo: maybe separated step?
                                .eventSource(eventSource)
                                .tokenStore(new InMemoryTokenStore())
                                .initialSegmentCount(1)
                );

        var configuration = MessagingConfigurer.create()
                                               .eventProcessing(eventProcessing -> eventProcessing
                                                       .defaults(defaults -> defaults
                                                               .shared(p -> p.errorHandler(PropagatingErrorHandler.instance()))
                                                               .pooledStreaming(p -> p.batchSize(100))
                                                       ).registerEventProcessorModule(module)
                                               )
                                               .build();

        // when
        configuration.start();

        // then
        await().atMost(Duration.ofSeconds(1))
               .untilAsserted(() -> assertThat(started).isTrue());

        // when
        configuration.shutdown();

        // then
        await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(stopped).isTrue());
    }
}