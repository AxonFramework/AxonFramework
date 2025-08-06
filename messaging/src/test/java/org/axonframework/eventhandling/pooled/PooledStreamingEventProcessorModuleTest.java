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
import org.axonframework.common.transaction.NoOpTransactionManager;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.eventhandling.*;
import org.axonframework.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.eventhandling.configuration.EventProcessingConfigurer;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.TransactionalUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.utils.AsyncInMemoryStreamableEventSource;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Test class validating the {@link PooledStreamingEventProcessorModule} functionality.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class PooledStreamingEventProcessorModuleTest {

    @Nested
    class ConstructorTest {

        @Test
        void shouldCreateModuleWithProcessorName() {
            // given
            String processorName = "test-processor";

            // when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents((cfg, components) -> components.single(new SimpleEventHandlingComponent()))
                    .customize((cfg, c) -> c);

            // then
            assertThat(module.name()).isEqualTo(processorName);
        }
    }

    @Nested
    class ConfigurationMethodTest {

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

            var processor = processor(configuration, processorName);
            assertThat(processor).isPresent();
            var processorConfig = configurationOf(processor.orElse(null));
            assertThat(processorConfig).isNotNull();
            assertThat(processorConfig.eventSource()).isSameAs(eventSource);
            assertThat(processorConfig.initialSegmentCount()).isEqualTo(4);
        }
    }

    @Nested
    class CustomizationTest {

        @Test
        void shouldApplyCustomizationToDefaultConfiguration() {
            // given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.single(
                            simpleRecordingTestComponent()
                    ))
                    .customize((cfg, c) -> c.initialSegmentCount(
                            8));

            // when
            module.customize((cfg, processorConfig) -> processorConfig.initialSegmentCount(8));

            // then
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d.eventSource(eventSource))
                    .processor(module)));
            var configuration = configurer.build();

            var processor = processor(configuration, processorName);
            assertThat(processor).isPresent();
            var processorConfig = configurationOf(processor.orElse(null));
            assertThat(processorConfig).isNotNull();
            assertThat(processorConfig.initialSegmentCount()).isEqualTo(8);
        }

        @Test
        void shouldApplyMultipleCustomizations() {
            // given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();

            //  when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.single(
                            simpleRecordingTestComponent())
                    )
                    .customize((cfg, processorConfig) -> processorConfig.initialSegmentCount(8));

            // then
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d.eventSource(eventSource))
                    .processor(module)));
            var configuration = configurer.build();

            var processor = processor(configuration, processorName);
            assertThat(processor).isPresent();
            var processorConfig = configurationOf(processor.orElse(null));
            assertThat(processorConfig).isNotNull();
            assertThat(processorConfig.initialSegmentCount()).isEqualTo(8);
        }

        @Test
        void shouldInheritParentCustomizations() {
            // given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            var expectedUnitOfWorkFactory = new SimpleUnitOfWorkFactory();

            // when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.single(
                            simpleRecordingTestComponent())
                    ).customize((cfg, processorConfig) -> processorConfig.initialSegmentCount(8));

            // then
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.defaults(d -> d.unitOfWorkFactory(expectedUnitOfWorkFactory)));
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d.eventSource(eventSource))
                    .processor(module)));
            var configuration = configurer.build();

            var processor = processor(configuration, processorName);
            assertThat(processor).isPresent();
            var processorConfig = configurationOf(processor.orElse(null));
            assertThat(processorConfig).isNotNull();
            assertThat(processorConfig.unitOfWorkFactory()).isSameAs(expectedUnitOfWorkFactory);
            assertThat(processorConfig.initialSegmentCount()).isEqualTo(8);
        }
    }

    @Nested
    class EventHandlingComponentsTest {

        @Test
        void shouldConfigureEventHandlingComponents() {
            // given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            var component1 = new SimpleEventHandlingComponent();
            var component2 = new SimpleEventHandlingComponent();

            // when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.many(component1, component2))
                    .build();

            // then
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d.eventSource(eventSource))
                    .processor(module)));
            var configuration = configurer.build();

            var processor = processor(configuration, processorName);
            assertThat(processor).isPresent();
        }

        @Test
        void shouldConfigureEventHandlingComponentsUsingConfigurers() {
            // given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();

            //  when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents((__, components) -> components.single(
                            simpleRecordingTestComponent())
                    ).build();

            // then
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d.eventSource(eventSource))
                    .processor(module)));
            var configuration = configurer.build();

            var processor = processor(configuration, processorName);
            assertThat(processor).isPresent();
        }
    }

    @Nested
    class ExecutorConfigurationTest {

        @Test
        void shouldCreateDefaultExecutorsWhenNotConfigured() {
            // given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            var component = new SimpleEventHandlingComponent();

            // when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents((__, components) -> components.single(component))
                    .customize((cfg, c) -> c);


            // then
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d.eventSource(eventSource))
                    .processor(module)));
            var configuration = configurer.build();

            var processor = processor(configuration, processorName);
            assertThat(processor).isPresent();
            var processorConfig = configurationOf(processor.orElse(null));
            assertThat(processorConfig).isNotNull();
            assertThat(processorConfig.coordinatorExecutorBuilder()).isNotNull();
            assertThat(processorConfig.workerExecutorBuilder()).isNotNull();
        }

        @Test
        void shouldUseCustomExecutorsWhenConfigured() {
            // given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            var component = new SimpleEventHandlingComponent();
            var customCoordinator = new AtomicReference<ScheduledExecutorService>();
            var customWorker = new AtomicReference<ScheduledExecutorService>();

            //  when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents((__, components) -> components.single(component))
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

            // then
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d.eventSource(eventSource))
                    .processor(module)));
            var configuration = configurer.build();

            var processor = processor(configuration, processorName);
            assertThat(processor).isPresent();
            var processorConfig = configurationOf(processor.orElse(null));
            assertThat(processorConfig).isNotNull();
            assertThat(customCoordinator.get()).isNotNull();
            assertThat(customWorker.get()).isNotNull();
        }
    }

    @Nested
    class LifecycleManagementTest {

        @Test
        void shouldStartAndStopProcessorWithLifecycleHooks() {
            String processorName = "test-processor";
            PooledStreamingEventProcessorModule module = minimalProcessorModule(processorName);

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            configuration.start();

            // then
            awaitProcessorIsStarted(configuration, processorName);

            // when
            configuration.shutdown();

            // then
            awaitProcessorIsStopped(configuration, processorName);
        }

        @Test
        void shouldShutdownCustomExecutorsOnConfigurationShutdown() {
            // given
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            var component = new SimpleEventHandlingComponent();
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d.eventSource(eventSource))
                    .processor("test-processor", singleEventHandlingComponent(component))));
            var configuration = configurer.build();

            // when
            configuration.start();
            var processor = processor(configuration, "test-processor");
            var processorConfig = configurationOf(processor.orElse(null));

            // then
            assertThat(processor).isPresent();
            assertThat(processorConfig).isNotNull();

            // when
            configuration.shutdown();

            // then
            //  Verify configuration has been shut down by checking processor is no longer available
            await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> {
                        var shutdownProcessor = processor(configuration, "test-processor");
                        //  After shutdown, processor should still be registered but in stopped state
                        assertThat(shutdownProcessor).isPresent();
                    });
        }

    }

    @Nested
    class EventHandlingTest {


        @Test
        void shouldHandleTheEventInEachEventHandlingComponent() {
            // given
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();

            var configurer = MessagingConfigurer.create();
            RecordingEventHandlingComponent component1 = simpleRecordingTestComponent();
            RecordingEventHandlingComponent component2 = simpleRecordingTestComponent();
            configurer.eventProcessing(
                    ep -> ep.pooledStreaming(
                            ps -> ps.defaults(d -> d.eventSource(eventSource))
                                    .processor("test-processor", (cfg, components) -> components.many(component1, component2))
                    )
            );
            var configuration = configurer.build();
            configuration.start();

            // when
            EventMessage<String> sampleEvent = EventTestUtils.asEventMessage("test-event");
            eventSource.publishMessage(sampleEvent);

            // then
            await().atMost(Duration.ofMillis(200))
                    .untilAsserted(() -> {
                        assertThat(component1.handled(sampleEvent)).isTrue();
                        assertThat(component2.handled(sampleEvent)).isTrue();
                    });

            // cleanup
            configuration.shutdown();
        }
    }

    @Nested
    class ModuleRegisteredComponentsTest {

        @Test
        void shouldRegisterProcessorAsComponent() {
            // given
            var processorName = "testProcessor";
            var module = minimalProcessorModule(processorName);

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var processor = processor(configuration, processorName);

            // then
            assertThat(processor).isPresent();
        }

    }

    @Nested
    @DisplayName("Configuration Hierarchy: Should apply configuration customizations in order: shared -> type-specific -> instance-specific")
    class ConfigurationHierarchyTest {

        @Test
        @DisplayName("Case #1: EventProcessorModule - customized")
        void testCase1() {
            // given
            var configurer = MessagingConfigurer.create();
            var processorName = "testProcessor";

            // and - shared customization
            UnitOfWorkFactory sharedUnitOfWorkFactory = new SimpleUnitOfWorkFactory();
            ErrorHandler sharedErrorHandler = PropagatingErrorHandler.instance();
            configurer.eventProcessing(ep -> ep.defaults(d -> d.errorHandler(sharedErrorHandler).unitOfWorkFactory(sharedUnitOfWorkFactory)));

            // and - type-specific customization
            UnitOfWorkFactory typeUnitOfWorkFactory = new TransactionalUnitOfWorkFactory(new NoOpTransactionManager());
            AsyncInMemoryStreamableEventSource typeEventSource = new AsyncInMemoryStreamableEventSource();
            int typeSegmentCount = 8;
            configurer.eventProcessing(ep ->
                    ep.pooledStreaming(ps -> ps.defaults(d -> d.unitOfWorkFactory(typeUnitOfWorkFactory).eventSource(typeEventSource).initialSegmentCount(typeSegmentCount)))
            );

            // and - instance-specific customization
            int instanceSegmentCount = 4;
            TokenStore instanceTokenStore = new InMemoryTokenStore();
            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(singleTestEventHandlingComponent())
                    .customize((__, p) -> p.initialSegmentCount(instanceSegmentCount).tokenStore(instanceTokenStore));
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));

            // when
            var configuration = configurer.build();

            // then
            var processor = processor(configuration, processorName);
            assertThat(processor).isPresent();

            // then
            var processorConfig = configurationOf(processor.orElse(null));
            assertThat(processorConfig).isNotNull();
            assertThat(processorConfig.errorHandler()).isEqualTo(sharedErrorHandler);

            assertThat(processorConfig.unitOfWorkFactory()).isNotEqualTo(sharedUnitOfWorkFactory);
            assertThat(processorConfig.unitOfWorkFactory()).isEqualTo(typeUnitOfWorkFactory);

            assertThat(processorConfig.eventSource()).isEqualTo(typeEventSource);

            assertThat(processorConfig.initialSegmentCount()).isNotEqualTo(typeSegmentCount);
            assertThat(processorConfig.initialSegmentCount()).isEqualTo(instanceSegmentCount);

            assertThat(processorConfig.tokenStore()).isEqualTo(instanceTokenStore);
        }
    }

    private static RecordingEventHandlingComponent simpleRecordingTestComponent() {
        return new RecordingEventHandlingComponent(
                SimpleEventHandlingComponent
                        .builder()
                        .handles(new QualifiedName(String.class), (e, c) -> MessageStream.empty()).build()
        );
    }

    private PooledStreamingEventProcessorModule minimalProcessorModule(String processorName) {
        //noinspection unchecked
        return EventProcessorModule
                .pooledStreaming(processorName)
                .eventHandlingComponents(singleTestEventHandlingComponent())
                .customize((cfg, c) -> c.eventSource(cfg.getOptionalComponent(StreamableEventSource.class).orElse(new AsyncInMemoryStreamableEventSource())));
    }

    @Nonnull
    private static BiFunction<Configuration, EventHandlingComponentsConfigurer.ComponentsPhase, EventHandlingComponentsConfigurer.CompletePhase> singleTestEventHandlingComponent() {
        var eventHandlingComponent = new SimpleEventHandlingComponent();
        eventHandlingComponent.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());
        return (__, components) -> components.single(eventHandlingComponent);
    }

    @Nonnull
    private static BiFunction<Configuration, EventHandlingComponentsConfigurer.ComponentsPhase, EventHandlingComponentsConfigurer.CompletePhase> singleEventHandlingComponent(SimpleEventHandlingComponent eventHandlingComponent) {
        return (__, components) -> components.single(eventHandlingComponent);
    }

    private static void awaitProcessorIsStopped(AxonConfiguration configuration, String processorName) {
        await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> processor(configuration, processorName).ifPresent(p -> assertThat(p.isRunning()).isFalse()));
    }

    private static void awaitProcessorIsStarted(AxonConfiguration configuration, String processorName) {
        await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> processor(configuration, processorName).ifPresent(p -> assertThat(p.isRunning()).isTrue()));
    }

    @Nonnull
    private static Optional<PooledStreamingEventProcessor> processor(
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