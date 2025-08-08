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
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.eventhandling.*;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventhandling.configuration.EventHandlingComponentsConfigurer;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Test class validating the {@link PooledStreamingEventProcessorModule} functionality and its integration with the
 * {@link MessagingConfigurer}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class PooledStreamingEventProcessorModuleTest {

    @Nested
    class ConstructionTest {

        @Test
        void shouldCreateModuleWithProcessorName() {
            // given
            String processorName = "test-processor";

            // when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.declarative(cfg -> new SimpleEventHandlingComponent()))
                    .customized((cfg, c) -> c);

            // then
            assertThat(module.name()).isEqualTo(processorName);
        }
    }

    @Nested
    class LifecycleManagementTest {

        @Test
        void registeredOnConfigurerShouldStartAndStopProcessorWithLifecycleHooks() {
            String processorName = "test-processor";
            PooledStreamingEventProcessorModule module = minimalProcessorModule(processorName);

            var configurer = MessagingConfigurer.create();
            configurer.componentRegistry(cr -> cr.registerModule(module));
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
        void registeredOnEventProcessingShouldStartAndStopProcessorWithLifecycleHooks() {
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

            var component3HandledPayload = new AtomicReference<String>();
            var component3 = new Object() {
                @EventHandler
                public void handle(String event) {
                    component3HandledPayload.set(event);
                }
            };
            configurer.eventProcessing(
                    ep -> ep.pooledStreaming(
                            ps -> ps.defaults(d -> d.eventSource(eventSource))
                                    .defaultProcessor("test-processor",
                                                      components -> components.declarative(cfg -> component1)
                                                                              .declarative(cfg -> component2)
                                                                              .annotated(cfg -> component3)
                                    )
                    )
            );
            var configuration = configurer.build();
            configuration.start();

            // when
            EventMessage<String> sampleEvent = EventTestUtils.asEventMessage("test-event");
            eventSource.publishMessage(sampleEvent);

            // then
            await().atMost(Duration.ofMillis(500))
                   .untilAsserted(() -> {
                       assertThat(component1.handled(sampleEvent)).isTrue();
                       assertThat(component2.handled(sampleEvent)).isTrue();
                       assertThat(component3HandledPayload.get()).isEqualTo(sampleEvent.payload());
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
            configurer.eventProcessing(ep -> ep.defaults(d -> d.errorHandler(sharedErrorHandler)
                                                               .unitOfWorkFactory(sharedUnitOfWorkFactory)));

            // and - type-specific customization
            UnitOfWorkFactory typeUnitOfWorkFactory = new TransactionalUnitOfWorkFactory(new NoOpTransactionManager());
            AsyncInMemoryStreamableEventSource typeEventSource = new AsyncInMemoryStreamableEventSource();
            int typeSegmentCount = 8;
            configurer.eventProcessing(ep ->
                                               ep.pooledStreaming(ps -> ps.defaults(
                                                       d -> d.unitOfWorkFactory(typeUnitOfWorkFactory)
                                                             .eventSource(typeEventSource)
                                                             .initialSegmentCount(typeSegmentCount))
                                               )
            );

            // and - instance-specific customization
            int instanceSegmentCount = 4;
            TokenStore instanceTokenStore = new InMemoryTokenStore();
            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(singleTestEventHandlingComponent())
                    .customized((__, p) -> p.initialSegmentCount(instanceSegmentCount).tokenStore(instanceTokenStore));
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

        @Test
        @DisplayName("Case #2: EventProcessorModule - customized with new configuration object")
        void testCase2() {
            // given
            var configurer = MessagingConfigurer.create();
            var processorName = "testProcessor";

            // and - shared customization
            UnitOfWorkFactory sharedUnitOfWorkFactory = new SimpleUnitOfWorkFactory();
            ErrorHandler sharedErrorHandler = PropagatingErrorHandler.instance();
            configurer.eventProcessing(ep -> ep.defaults(d -> d.errorHandler(sharedErrorHandler)
                                                               .unitOfWorkFactory(sharedUnitOfWorkFactory)));

            // and - type-specific customization
            UnitOfWorkFactory typeUnitOfWorkFactory = new TransactionalUnitOfWorkFactory(new NoOpTransactionManager());
            AsyncInMemoryStreamableEventSource typeEventSource = new AsyncInMemoryStreamableEventSource();
            int typeSegmentCount = 8;
            configurer.eventProcessing(ep ->
                                               ep.pooledStreaming(ps -> ps.defaults(
                                                                          d -> d.unitOfWorkFactory(typeUnitOfWorkFactory)
                                                                                .eventSource(typeEventSource)
                                                                                .initialSegmentCount(typeSegmentCount)
                                                                  )
                                               )
            );

            // and - instance-specific customization
            int instanceSegmentCount = 4;
            TokenStore instanceTokenStore = new InMemoryTokenStore();
            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(singleTestEventHandlingComponent())
                    .customized((__, p) -> new PooledStreamingEventProcessorConfiguration()
                            .eventSource(p.eventSource())
                            .tokenStore(instanceTokenStore)
                            .initialSegmentCount(instanceSegmentCount));
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
            assertThat(processorConfig.unitOfWorkFactory()).isNotEqualTo(typeUnitOfWorkFactory);

            assertThat(processorConfig.eventSource()).isEqualTo(typeEventSource);

            assertThat(processorConfig.initialSegmentCount()).isNotEqualTo(typeSegmentCount);
            assertThat(processorConfig.initialSegmentCount()).isEqualTo(instanceSegmentCount);

            assertThat(processorConfig.tokenStore()).isEqualTo(instanceTokenStore);
        }

        @Test
        @DisplayName("Case #3: EventProcessorModule - build with type specific configuration")
        void testCase3() {
            // given
            var configurer = MessagingConfigurer.create();
            var processorName = "testProcessor";

            // and - shared customization
            UnitOfWorkFactory sharedUnitOfWorkFactory = new SimpleUnitOfWorkFactory();
            ErrorHandler sharedErrorHandler = PropagatingErrorHandler.instance();
            configurer.eventProcessing(ep -> ep.defaults(d -> d.errorHandler(sharedErrorHandler)
                                                               .unitOfWorkFactory(sharedUnitOfWorkFactory)));

            // and - type-specific customization
            UnitOfWorkFactory typeUnitOfWorkFactory = new TransactionalUnitOfWorkFactory(new NoOpTransactionManager());
            AsyncInMemoryStreamableEventSource typeEventSource = new AsyncInMemoryStreamableEventSource();
            int typeSegmentCount = 8;
            configurer.eventProcessing(ep ->
                                               ep.pooledStreaming(ps -> ps.defaults(
                                                       d -> d.unitOfWorkFactory(typeUnitOfWorkFactory)
                                                             .eventSource(typeEventSource)
                                                             .initialSegmentCount(typeSegmentCount))
                                               )
            );

            // and - instance-specific customization
            configurer.eventProcessing(ep -> ep.pooledStreaming(
                                               ps -> ps.processor(
                                                       processorName,
                                                       p -> p.eventHandlingComponents(c -> c.declarative(cfg -> simpleRecordingTestComponent()))
                                                             .notCustomized()
                                               )
                                       )
            );

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
            assertThat(processorConfig.initialSegmentCount()).isEqualTo(typeSegmentCount);
        }

        @Test
        @DisplayName("Case #4: EventProcessorModule - globally registered event source")
        void testCase4() {
            // given
            var configurer = MessagingConfigurer.create();
            var processorName = "testProcessor";

            AsyncInMemoryStreamableEventSource globalEventSource = new AsyncInMemoryStreamableEventSource();
            configurer.componentRegistry(cr -> cr.registerComponent(StreamableEventSource.class,
                                                                    cfg -> globalEventSource));

            // and
            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(singleTestEventHandlingComponent())
                    .notCustomized();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));

            // when
            var configuration = configurer.build();

            // then
            var processor = processor(configuration, processorName);
            assertThat(processor).isPresent();

            // then
            var processorConfig = configurationOf(processor.orElse(null));
            assertThat(processorConfig).isNotNull();
            assertThat(processorConfig.eventSource()).isEqualTo(globalEventSource);
        }

        @Test
        @DisplayName("Case #5: EventProcessing defaults do not override global components")
        void testCase5() {
            // given
            var configurer = MessagingConfigurer.create();
            var processorName = "testProcessor";

            AsyncInMemoryStreamableEventSource globalEventSource = new AsyncInMemoryStreamableEventSource();
            configurer.componentRegistry(cr -> cr.registerComponent(StreamableEventSource.class,
                                                                    cfg -> globalEventSource));

            // and - type-specific customization
            AsyncInMemoryStreamableEventSource typeEventSource = new AsyncInMemoryStreamableEventSource();
            configurer.eventProcessing(ep ->
                                               ep.pooledStreaming(ps -> ps.defaults((cfg, d) -> d.eventSource(
                                                       typeEventSource)))
            );

            // and
            @SuppressWarnings("unchecked")
            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(singleTestEventHandlingComponent())
                    .customized((axonConfig, customization) -> customization.eventSource(axonConfig.getComponent(
                            StreamableEventSource.class)));
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));

            // when
            var configuration = configurer.build();

            // then
            var processor = processor(configuration, processorName);
            assertThat(processor).isPresent();

            // then
            var processorConfig = configurationOf(processor.orElse(null));
            assertThat(processorConfig).isNotNull();
            assertThat(processorConfig.eventSource()).isNotEqualTo(typeEventSource);
            assertThat(processorConfig.eventSource()).isEqualTo(globalEventSource);
        }

        @Test
        @DisplayName("Case #6: EventProcessing defaults do not register components on the MessagingConfigurer level")
        void testCase6() {
            // given
            var configurer = MessagingConfigurer.create();

            // and
            AsyncInMemoryStreamableEventSource typeEventSource = new AsyncInMemoryStreamableEventSource();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.defaults(d -> d.eventSource(typeEventSource)))
            );

            // when
            var configuration = configurer.build();

            // then
            var eventSource = configuration.getOptionalComponent(StreamableEventSource.class);
            assertThat(eventSource).isNotPresent();
        }

        @DisplayName("Case #7: PooledStreamingEventProcessorModule - Custom executor factories")
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
                    .eventHandlingComponents((components) -> components.declarative(cfg -> component))
                    .customized((cfg, processorConfig) -> processorConfig
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
                .customized((cfg, c) -> c.eventSource(cfg.getOptionalComponent(StreamableEventSource.class)
                                                         .orElse(new AsyncInMemoryStreamableEventSource())));
    }

    @Nonnull
    private static Function<EventHandlingComponentsConfigurer.RequiredComponentPhase, EventHandlingComponentsConfigurer.CompletePhase> singleTestEventHandlingComponent() {
        var eventHandlingComponent = new SimpleEventHandlingComponent();
        eventHandlingComponent.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());
        return (components) -> components.declarative(cfg -> eventHandlingComponent);
    }

    private static void awaitProcessorIsStopped(AxonConfiguration configuration, String processorName) {
        await().atMost(Duration.ofSeconds(1))
               .untilAsserted(() -> processor(configuration,
                                              processorName).ifPresent(p -> assertThat(p.isRunning()).isFalse()));
    }

    private static void awaitProcessorIsStarted(AxonConfiguration configuration, String processorName) {
        await().atMost(Duration.ofSeconds(1))
               .untilAsserted(() -> processor(configuration,
                                              processorName).ifPresent(p -> assertThat(p.isRunning()).isTrue()));
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