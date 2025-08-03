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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.MonitoringEventHandlingComponent;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.configuration.NewEventProcessingModule;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.utils.AsyncInMemoryStreamableEventSource;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.axonframework.eventhandling.configuration.EventHandlingComponents.*;
import static org.axonframework.eventhandling.configuration.EventHandlingComponents.Definition.*;

class PooledStreamingEventProcessorModuleTest {

    @Test
    void appliesDefaultConfiguration() {
        // given
        AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();

        var expectedUnitOfWorkFactory = new SimpleUnitOfWorkFactory();
        var configurer = MessagingConfigurer.create();
        configurer.eventProcessing(
                ep -> ep.defaults(
                        d -> d.unitOfWorkFactory(expectedUnitOfWorkFactory)
                )
        );
        var processorName = "testProcessor";
        configurer.eventProcessing(
                ep -> ep.pooledStreaming(
                        ps -> ps
                                .defaults(d -> d.eventSource(eventSource))
                                .processor(processorName, single(new SimpleEventHandlingComponent()))
                )
        );
        var configuration = configurer.build();

        // when
        var processor = configuredProcessor(configuration, processorName);
        assertThat(processor).isPresent();
        var processorConfig = configurationOf(processor.orElse(null));
        assertThat(processorConfig).isNotNull();
        assertThat(processorConfig.unitOfWorkFactory()).isEqualTo(expectedUnitOfWorkFactory);
    }

    @Test
    void registersAsComponent() {
        // given
        AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();

        var expectedUnitOfWorkFactory = new SimpleUnitOfWorkFactory();
        var configurer = MessagingConfigurer.create();
        configurer.eventProcessing(
                ep -> ep.defaults(
                        d -> d.unitOfWorkFactory(expectedUnitOfWorkFactory)
                )
        );
        var processorName = "testProcessor";
        configurer.eventProcessing(
                ep -> ep.pooledStreaming(
                        ps -> ps.defaults(d -> d.eventSource(eventSource))
                                .processor(processorName, single(new SimpleEventHandlingComponent()))
                )
        );
        var configuration = configurer.build();

        // when
        var processor = configuredProcessor(configuration, processorName);

        assertThat(processor).isPresent();
    }

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
        PooledStreamingEventProcessorModule module = EventProcessorModule
                .pooledStreaming("test-processor")
                .eventHandlingComponents(many(eventHandlingComponent1, eventHandlingComponent2))
                .customize(cfg -> customization ->
                        customization.initialSegmentCount(1)
                );

        var configurer = MessagingConfigurer.create();
        configurer.eventProcessing(
                ep -> ep.pooledStreaming(
                        ps -> ps
                                .defaults(d -> d.eventSource(eventSource))
                                .processor(module)
                )
        );
        var configuration = configurer.build();

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

    @Test
    void registersWithLifecycleHooks2() {
        // given
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean stopped = new AtomicBoolean(false);
        AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
        eventSource.setOnOpen(() -> started.set(true));
        eventSource.setOnClose(() -> stopped.set(true));

        var configurer = MessagingConfigurer.create();
        configurer.eventProcessing(
                ep -> ep.pooledStreaming(
                        ps -> ps
                                .defaults(d -> d.eventSource(eventSource))
                                .processor("test-processor",
                                           many(
                                                   component()
                                                           .sequenceIdentifier(EventMessage::getTimestamp)
                                                           .handles(
                                                                   new QualifiedName(String.class),
                                                                   (e, c) -> MessageStream.empty()
                                                           ),
                                                   component()
                                                           .handles(
                                                                   new QualifiedName(Integer.class),
                                                                   (e, c) -> MessageStream.empty()
                                                           )
                                           ).decorated(c -> new MonitoringEventHandlingComponent(NoOpMessageMonitor.instance(), c))
                                )
                )
        );
        var configuration = configurer.build();

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

    @Nonnull
    private static Optional<PooledStreamingEventProcessor> configuredProcessor(AxonConfiguration configuration,
                                                                               String processorName) {
        return configuration
                .getModuleConfiguration(NewEventProcessingModule.DEFAULT_NAME)
                .flatMap(m -> m.getModuleConfiguration(PooledStreamingEventProcessorsModule.DEFAULT_NAME))
                .flatMap(m -> m.getModuleConfiguration(processorName))
                .flatMap(m -> m.getOptionalComponent(PooledStreamingEventProcessor.class, processorName));
    }

    @Nullable
    private static PooledStreamingEventProcessorConfiguration configurationOf(PooledStreamingEventProcessor processor) {
        return Optional.ofNullable(processor).map(p -> {
            try {
                var field = p.getClass().getDeclaredField("configuration");
                field.setAccessible(true);
                return (PooledStreamingEventProcessorConfiguration) field.get(p);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).orElse(null);
    }
}