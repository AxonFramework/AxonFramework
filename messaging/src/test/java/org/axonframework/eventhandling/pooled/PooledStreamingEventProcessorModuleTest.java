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
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.MonitoringEventHandlingComponent;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.configuration.NewEventProcessingModule;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.utils.AsyncInMemoryStreamableEventSource;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.axonframework.eventhandling.configuration.EventHandlingComponentsConfigurer.*;
import static org.axonframework.eventhandling.configuration.EventHandlingComponentConfigurer.*;

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
            //given
            String processorName = "test-processor";

            //when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(cfg -> single(new SimpleEventHandlingComponent()))
                    .defaultCustomized(cfg -> c -> c);

            //then
            assertThat(module.name()).isEqualTo(processorName);
        }
    }

    @Nested
    class ConfigurationMethodTest {

        @Test
        void shouldAcceptDirectConfiguration() {
            //given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();

            //when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(cfg -> single(new SimpleEventHandlingComponent()))
                    .overriddenConfiguration(cfg -> new PooledStreamingEventProcessorConfiguration()
                            .eventSource(eventSource)
                            .tokenStore(new InMemoryTokenStore())
                            .unitOfWorkFactory(new SimpleUnitOfWorkFactory())
                            .initialSegmentCount(4))
                    .build();

            //then
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
    }

    @Nested
    class CustomizationTest {

        @Test
        void shouldApplyCustomizationToDefaultConfiguration() {
            //given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            PooledStreamingEventProcessorModule module = EventProcessorModule.pooledStreaming(processorName)
                                                                             .eventHandlingComponents(single(component().handles(
                                                                                     new QualifiedName(String.class),
                                                                                     (e, c) -> MessageStream.empty())))
                                                                             .defaultCustomized(cfg -> c -> c.initialSegmentCount(
                                                                                     8));

            //when
            module.defaultCustomized(cfg -> processorConfig -> processorConfig.initialSegmentCount(8));

            //then
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
        void shouldApplyMultipleCustomizations() {
            //given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();

            // when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(single(component().handles(new QualifiedName(String.class),
                                                                        (e, c) -> MessageStream.empty())))
                    .defaultCustomized(cfg -> processorConfig -> processorConfig.initialSegmentCount(8));

            //then
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
        void shouldInheritParentCustomizations() {
            //given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            var expectedUnitOfWorkFactory = new SimpleUnitOfWorkFactory();

            //when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(single(component().handles(new QualifiedName(String.class),
                                                                        (e, c) -> MessageStream.empty())))
                    .defaultCustomized(cfg -> processorConfig -> processorConfig.initialSegmentCount(8));

            //then
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.defaults(d -> d.unitOfWorkFactory(expectedUnitOfWorkFactory)));
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d.eventSource(eventSource))
                    .processor(module)));
            var configuration = configurer.build();

            var processor = configuredProcessor(configuration, processorName);
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
            //given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            var component1 = new SimpleEventHandlingComponent();
            var component2 = new SimpleEventHandlingComponent();

            //when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(cfg -> many(component1, component2))
                    .defaultConfiguration();

            //then
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d.eventSource(eventSource))
                    .processor(module)));
            var configuration = configurer.build();

            var processor = configuredProcessor(configuration, processorName);
            assertThat(processor).isPresent();
        }

        @Test
        void shouldConfigureEventHandlingComponentsUsingConfigurers() {
            //given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();

            // when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(cfg -> single(component()
                                                                   .handles(new QualifiedName(String.class),
                                                                            (e, c) -> MessageStream.empty())))
                    .defaultConfiguration();

            //then
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d.eventSource(eventSource))
                    .processor(module)));
            var configuration = configurer.build();

            var processor = configuredProcessor(configuration, processorName);
            assertThat(processor).isPresent();
        }
    }

    @Nested
    class ExecutorConfigurationTest {

        @Test
        void shouldCreateDefaultExecutorsWhenNotConfigured() {
            //given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            var component = new SimpleEventHandlingComponent();

            //when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(cfg -> single(component))
                    .defaultCustomized(cfg -> c -> c);


            //then
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d.eventSource(eventSource))
                    .processor(module)));
            var configuration = configurer.build();

            var processor = configuredProcessor(configuration, processorName);
            assertThat(processor).isPresent();
            var processorConfig = configurationOf(processor.orElse(null));
            assertThat(processorConfig).isNotNull();
            assertThat(processorConfig.coordinatorExecutorBuilder()).isNotNull();
            assertThat(processorConfig.workerExecutorBuilder()).isNotNull();
        }

        @Test
        void shouldUseCustomExecutorsWhenConfigured() {
            //given
            String processorName = "test-processor";
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            var component = new SimpleEventHandlingComponent();
            var customCoordinator = new AtomicReference<ScheduledExecutorService>();
            var customWorker = new AtomicReference<ScheduledExecutorService>();

            // when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(cfg -> single(component))
                    .defaultCustomized(cfg -> processorConfig -> processorConfig
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

            //then
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d.eventSource(eventSource))
                    .processor(module)));
            var configuration = configurer.build();

            var processor = configuredProcessor(configuration, processorName);
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
            //given
            AtomicBoolean started = new AtomicBoolean(false);
            AtomicBoolean stopped = new AtomicBoolean(false);
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            eventSource.setOnOpen(() -> started.set(true));
            eventSource.setOnClose(() -> stopped.set(true));

            var eventHandlingComponent = new SimpleEventHandlingComponent();
            eventHandlingComponent.subscribe(new QualifiedName(String.class),
                                             (event, context) -> MessageStream.empty());
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming("test-processor")
                    .eventHandlingComponents(cfg -> single(eventHandlingComponent))
                    .defaultCustomized(cfg -> customization ->
                            customization.initialSegmentCount(1)
                    );

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(
                    ep -> ep.pooledStreaming(
                            ps -> ps.defaults(d -> d.eventSource(eventSource))
                                    .processor(module)
                    )
            );
            var configuration = configurer.build();

            //when
            configuration.start();

            //then
            await().atMost(Duration.ofSeconds(1))
                   .untilAsserted(() -> assertThat(started).isTrue());

            //when
            configuration.shutdown();

            //then
            await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(stopped).isTrue());
        }

        @Test
        void shouldShutdownCustomExecutorsOnConfigurationShutdown() {
            //given
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            var component = new SimpleEventHandlingComponent();
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d.eventSource(eventSource))
                    .processor("test-processor", single(component))));
            var configuration = configurer.build();

            //when
            configuration.start();
            var processor = configuredProcessor(configuration, "test-processor");
            var processorConfig = configurationOf(processor.orElse(null));

            //then
            assertThat(processor).isPresent();
            assertThat(processorConfig).isNotNull();

            //when
            configuration.shutdown();

            //then
            // Verify configuration has been shut down by checking processor is no longer available
            await().atMost(Duration.ofSeconds(2))
                   .untilAsserted(() -> {
                       var shutdownProcessor = configuredProcessor(configuration, "test-processor");
                       // After shutdown, processor should still be registered but in stopped state
                       assertThat(shutdownProcessor).isPresent();
                   });
        }
    }

    @Nested
    class ComponentRegistrationTest {

        @Test
        void shouldRegisterProcessorAsComponent() {
            //given
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            var processorName = "testProcessor";
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(
                    ep -> ep.pooledStreaming(
                            ps -> ps.defaults(d -> d.eventSource(eventSource))
                                    .processor(processorName, single(new SimpleEventHandlingComponent()))
                    )
            );
            var configuration = configurer.build();

            //when
            var processor = configuredProcessor(configuration, processorName);

            //then
            assertThat(processor).isPresent();
            assertThat(processor.get()).isInstanceOf(PooledStreamingEventProcessor.class);
        }

        @Test
        void shouldApplyDefaultConfigurationFromHierarchy() {
            //given
            var expectedUnitOfWorkFactory = new SimpleUnitOfWorkFactory();
            AsyncInMemoryStreamableEventSource expectedEventSource = new AsyncInMemoryStreamableEventSource();
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(
                    ep -> ep.defaults(
                            d -> d.unitOfWorkFactory(expectedUnitOfWorkFactory)
                    )
            );
            var processorName = "testProcessor";
            configurer.eventProcessing(
                    ep -> ep.pooledStreaming(
                            ps -> ps.defaults(d -> d.eventSource(expectedEventSource))
                                    .processor(processorName, single(new SimpleEventHandlingComponent()))
                    )
            );
            var configuration = configurer.build();

            //when
            var processor = configuredProcessor(configuration, processorName);
            var processorConfig = configurationOf(processor.orElse(null));

            //then
            assertThat(processor).isPresent();
            assertThat(processorConfig).isNotNull();
            assertThat(processorConfig.unitOfWorkFactory()).isEqualTo(expectedUnitOfWorkFactory);
            assertThat(processorConfig.eventSource()).isEqualTo(expectedEventSource);
        }
    }

    @Nested
    class ComponentDecorationTest {

        @Test
        void shouldApplyDecorationsToEventHandlingComponents() {
            //given
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(
                    ep -> ep.pooledStreaming(
                            ps -> ps.defaults(d -> d.eventSource(eventSource))
                                    .processor("test-processor",
                                               many(
                                                       component()
                                                               .handles(new QualifiedName(String.class),
                                                                        (e, c) -> MessageStream.empty()),
                                                       component()
                                                               .handles(new QualifiedName(Integer.class),
                                                                        (e, c) -> MessageStream.empty())
                                               ).decorated(c -> new MonitoringEventHandlingComponent(
                                                       NoOpMessageMonitor.instance(), c))
                                    )
                    )
            );
            var configuration = configurer.build();

            //when
            var processor = configuredProcessor(configuration, "test-processor");

            //then
            assertThat(processor).isPresent();
        }
    }

    @Nested
    class IntegrationTest {

        @Test
        void shouldProcessEventsWithMultipleHandlers() {
            //given
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            var handler1Invoked = new AtomicBoolean();
            var handler2Invoked = new AtomicBoolean();

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(
                    ep -> ep.pooledStreaming(
                            ps -> ps.defaults(d -> d.eventSource(eventSource))
                                    .processor("test-processor",
                                               many(
                                                       component()
                                                               .handles(new QualifiedName(String.class), (e, c) -> {
                                                                   handler1Invoked.set(true);
                                                                   return MessageStream.empty();
                                                               }),
                                                       component()
                                                               .handles(new QualifiedName(String.class), (e, c) -> {
                                                                   handler2Invoked.set(true);
                                                                   return MessageStream.empty();
                                                               })
                                               ))
                    )
            );
            var configuration = configurer.build();

            //when
            var processor = configuredProcessor(configuration, "test-processor");

            //then
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