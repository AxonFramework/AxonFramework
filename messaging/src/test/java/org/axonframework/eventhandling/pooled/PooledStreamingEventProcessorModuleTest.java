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
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.configuration.AbstractEventProcessorModuleTestSuite;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.utils.AsyncInMemoryStreamableEventSource;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Test class validating the {@link PooledStreamingEventProcessorModule} functionality.
 * <p>
 * This test class extends {@link AbstractEventProcessorModuleTestSuite} to inherit common test scenarios
 * while adding pooled streaming specific functionality tests.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class PooledStreamingEventProcessorModuleTest extends AbstractEventProcessorModuleTestSuite<
        PooledStreamingEventProcessor,
        PooledStreamingEventProcessorConfiguration,
        PooledStreamingEventProcessorModule> {

    @Override
    protected EventProcessorModule.EventHandlingPhase<PooledStreamingEventProcessorModule, PooledStreamingEventProcessorConfiguration> createModuleBuilder(String processorName) {
        return EventProcessorModule.pooledStreaming(processorName);
    }

    @Override
    protected Class<PooledStreamingEventProcessor> getProcessorType() {
        return PooledStreamingEventProcessor.class;
    }

    @Override
    protected Class<PooledStreamingEventProcessorConfiguration> getConfigurationType() {
        return PooledStreamingEventProcessorConfiguration.class;
    }

    @Override
    protected void configureRequiredDefaults(MessagingConfigurer configurer) {
        configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                .defaults(d -> d.eventSource(new AsyncInMemoryStreamableEventSource()))));
    }

    @Override
    protected PooledStreamingEventProcessorConfiguration getProcessorConfiguration(PooledStreamingEventProcessor processor) {
        return configurationOf(processor);
    }

    @Override
    protected PooledStreamingEventProcessorConfiguration createDefaultConfiguration() {
        return new PooledStreamingEventProcessorConfiguration()
                .eventSource(new AsyncInMemoryStreamableEventSource())
                .tokenStore(new InMemoryTokenStore())
                .unitOfWorkFactory(new SimpleUnitOfWorkFactory());
    }

    @Override
    protected EventHandlingComponent createLifecycleAwareEventHandlingComponent(AtomicBoolean startedFlag,
                                                                               AtomicBoolean stoppedFlag) {
        var eventHandlingComponent = new SimpleEventHandlingComponent();
        eventHandlingComponent.subscribe(new QualifiedName(String.class),
                                       (event, context) -> {
                                           startedFlag.set(true);
                                           return MessageStream.empty();
                                       });
        return eventHandlingComponent;
    }

    @Nested
    class PooledStreamingSpecificTests {

        @Test
        void shouldAcceptDirectConfiguration() {
            // given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();

            // when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents((__, components) -> components.single(new SimpleEventHandlingComponent()))
                    .configure(cfg -> new PooledStreamingEventProcessorConfiguration()
                            .eventSource(eventSource)
                            .tokenStore(new InMemoryTokenStore())
                            .unitOfWorkFactory(new SimpleUnitOfWorkFactory())
                            .initialSegmentCount(4))
                    .build();

            // then
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            var processor = configuredProcessor(configuration, processorName);
            assertThat(processor).isPresent();
            var processorConfig = configurationOf(processor.orElse(null));
            assertThat(processorConfig).isNotNull();
            assertThat(processorConfig.eventSource()).isSameAs(eventSource);
            assertThat(processorConfig.initialSegmentCount()).isEqualTo(4);
        }

        @Test
        void shouldApplyCustomizationToDefaultConfiguration() {
            // given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.single(
                            SimpleEventHandlingComponent.builder().handles(
                                    new QualifiedName(String.class),
                                    (e, c) -> MessageStream.empty()
                            ).build()
                    ))
                    .customize((cfg, c) -> c.initialSegmentCount(8));

            // when
            module.customize((cfg, processorConfig) -> processorConfig.initialSegmentCount(8));

            // then
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d.eventSource(eventSource))
                    .processor(module)));
            var configuration = configurer.build();

            var processor = configuredProcessor(configuration, processorName);
            assertThat(processor).isPresent();
            var processorConfig = configurationOf(processor.orElse(null));
            assertThat(processorConfig).isNotNull();
            assertThat(processorConfig.initialSegmentCount()).isEqualTo(8);
        }

        @Test
        void shouldConfigureSegmentCount() {
            // given
            String processorName = "segment-test-processor";
            int expectedSegmentCount = 16;

            // when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponent(createTestEventHandlingComponent())
                    .customize((cfg, processorConfig) -> processorConfig.initialSegmentCount(expectedSegmentCount));

            var configurer = createBaseConfigurer();
            configurer.componentRegistry(cr -> cr.registerModule(module));
            var configuration = configurer.build();

            // then
            var processor = getConfiguredProcessor(configuration, processorName);
            assertThat(processor).isPresent();
            var processorConfig = getProcessorConfiguration(processor.get());
            assertThat(processorConfig.initialSegmentCount()).isEqualTo(expectedSegmentCount);
        }

        @Test
        void shouldConfigureBatchSize() {
            // given
            String processorName = "batch-size-test-processor";
            int expectedBatchSize = 256;

            // when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponent(createTestEventHandlingComponent())
                    .customize((cfg, processorConfig) -> processorConfig.batchSize(expectedBatchSize));

            var configurer = createBaseConfigurer();
            configurer.componentRegistry(cr -> cr.registerModule(module));
            var configuration = configurer.build();

            // then
            var processor = getConfiguredProcessor(configuration, processorName);
            assertThat(processor).isPresent();
            var processorConfig = getProcessorConfiguration(processor.get());
            assertThat(processorConfig.batchSize()).isEqualTo(expectedBatchSize);
        }

        @Test
        void shouldUseCustomExecutorsWhenConfigured() {
            // given
            String processorName = "executor-test-processor";
            var customCoordinator = new AtomicReference<ScheduledExecutorService>();
            var customWorker = new AtomicReference<ScheduledExecutorService>();

            // when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponent(createTestEventHandlingComponent())
                    .customize((cfg, processorConfig) -> processorConfig
                            .coordinatorExecutor(name -> {
                                var executor = java.util.concurrent.Executors.newScheduledThreadPool(1);
                                customCoordinator.set(executor);
                                return executor;
                            })
                            .workerExecutor(name -> {
                                var executor = java.util.concurrent.Executors.newScheduledThreadPool(2);
                                customWorker.set(executor);
                                return executor;
                            }));

            var configurer = createBaseConfigurer();
            configurer.componentRegistry(cr -> cr.registerModule(module));
            var configuration = configurer.build();

            // then
            var processor = getConfiguredProcessor(configuration, processorName);
            assertThat(processor).isPresent();
            var processorConfig = getProcessorConfiguration(processor.get());
            assertThat(processorConfig).isNotNull();
            assertThat(customCoordinator.get()).isNotNull();
            assertThat(customWorker.get()).isNotNull();
        }

        @Test
        void shouldProcessEventsWithMultipleHandlers() {
            // given
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            var handler1Invoked = new AtomicBoolean();
            var handler2Invoked = new AtomicBoolean();

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(
                    ep -> ep.pooledStreaming(
                            ps -> ps.defaults(d -> d.eventSource(eventSource))
                                    .processor("test-processor",
                                               (cfg, components) -> components.many(
                                                       SimpleEventHandlingComponent
                                                               .builder()
                                                               .handles(new QualifiedName(String.class),
                                                                        (e, c) -> {
                                                                            handler1Invoked.set(true);
                                                                            return MessageStream.empty();
                                                                        }).build(),
                                                       SimpleEventHandlingComponent
                                                               .builder()
                                                               .handles(new QualifiedName(String.class),
                                                                        (e, c) -> {
                                                                            handler2Invoked.set(true);
                                                                            return MessageStream.empty();
                                                                        }).build()
                                               ))
                    )
            );
            var configuration = configurer.build();

            // when
            var processor = configuredProcessor(configuration, "test-processor");

            // then
            assertThat(processor).isPresent();
            configuration.start();

            EventMessage<String> sampleEvent = EventTestUtils.asEventMessage("test-event");
            eventSource.publishMessage(sampleEvent);

            await().atMost(Duration.ofSeconds(2))
                   .untilAsserted(() -> {
                       assertThat(handler1Invoked).isTrue();
                       assertThat(handler2Invoked).isTrue();
                   });

            configuration.shutdown();
        }
    }

    @Nonnull
    private static Optional<PooledStreamingEventProcessor> configuredProcessor(
            AxonConfiguration configuration,
            String processorName
    ) {
        return configuration.getModuleConfiguration(processorName)
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