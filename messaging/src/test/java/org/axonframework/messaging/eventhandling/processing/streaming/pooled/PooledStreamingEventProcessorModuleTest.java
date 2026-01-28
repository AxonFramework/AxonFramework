/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.processing.streaming.pooled;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.core.EmptyApplicationContext;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.core.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.TransactionalUnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkTestUtils;
import org.axonframework.messaging.core.unitofwork.transaction.NoOpTransactionManager;
import org.axonframework.messaging.eventhandling.AsyncInMemoryStreamableEventSource;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.eventhandling.RecordingEventHandlingComponent;
import org.axonframework.messaging.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.axonframework.messaging.eventhandling.processing.errorhandling.ErrorHandler;
import org.axonframework.messaging.eventhandling.processing.errorhandling.PropagatingErrorHandler;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.deadletter.InMemorySequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.deadletter.CachingSequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.deadletter.DeadLetterQueueConfiguration;
import org.axonframework.messaging.eventstreaming.StreamableEventSource;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
                    .eventHandlingComponents(components -> components.declarative(
                            cfg -> SimpleEventHandlingComponent.create("test")
                    ))
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
                                                                              .autodetected(cfg -> component3)
                                    )
                    )
            );
            var configuration = configurer.build();
            configuration.start();

            // when
            EventMessage sampleEvent = EventTestUtils.asEventMessage("test-event");
            eventSource.publishMessage(sampleEvent);

            // then
            await().untilAsserted(() -> {
                assertThat(component1.handled(sampleEvent)).isTrue();
                assertThat(component2.handled(sampleEvent)).isTrue();
                assertThat(component3HandledPayload.get()).isEqualTo(sampleEvent.payload());
            });

            // cleanup
            configuration.shutdown();
        }
    }

    @Nested
    class InterceptorTest {

        @Test
        void registeredInterceptorsShouldBeInvoked() {
            // given...
            MessagingConfigurer configurer = MessagingConfigurer.create();
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();
            // Build a global interceptor on the configurer, a default used by both modules, and a specific interceptor per module
            AtomicInteger invokedGlobal = new AtomicInteger(0);
            AtomicInteger invokedDefault = new AtomicInteger(0);
            AtomicBoolean invokedSpecificOne = new AtomicBoolean(false);
            AtomicBoolean invokedSpecificTwo = new AtomicBoolean(false);
            MessageHandlerInterceptor<EventMessage> globalInterceptor = (event, context, chain) -> {
                invokedGlobal.incrementAndGet();
                return chain.proceed(event, context);
            };
            MessageHandlerInterceptor<EventMessage> defaultInterceptor = (event, context, chain) -> {
                invokedDefault.incrementAndGet();
                return chain.proceed(event, context);
            };
            MessageHandlerInterceptor<EventMessage> specificInterceptorOne = (event, context, chain) -> {
                invokedSpecificOne.set(true);
                return chain.proceed(event, context);
            };
            MessageHandlerInterceptor<EventMessage> specificInterceptorTwo = (event, context, chain) -> {
                invokedSpecificTwo.set(true);
                return chain.proceed(event, context);
            };
            // Construct two components, each within their own PSEP
            RecordingEventHandlingComponent componentOne = simpleRecordingTestComponent(new QualifiedName(String.class));
            PooledStreamingEventProcessorModule psepModuleOne =
                    EventProcessorModule.pooledStreaming("processor-one")
                                        .eventHandlingComponents(
                                                components -> components.declarative(cfg -> componentOne)
                                        )
                                        .customized((config, psepConfig) -> psepConfig.withInterceptor(
                                                specificInterceptorOne
                                        ));
            RecordingEventHandlingComponent componentTwo = simpleRecordingTestComponent(new QualifiedName(Integer.class));
            PooledStreamingEventProcessorModule psepModuleTwo =
                    EventProcessorModule.pooledStreaming("processor-two")
                                        .eventHandlingComponents(
                                                components -> components.declarative(cfg -> componentTwo)
                                        )
                                        .customized((config, psepConfig) -> psepConfig.withInterceptor(
                                                specificInterceptorTwo
                                        ));
            // Register the global interceptor
            configurer.registerEventHandlerInterceptor(c -> globalInterceptor);
            // Register the default interceptor and attach both PSEP modules.
            configurer.eventProcessing(processingConfigurer -> processingConfigurer.pooledStreaming(
                    psepConfigurer -> psepConfigurer.defaults(
                                                            defaults -> defaults.eventSource(eventSource)
                                                                                .withInterceptor(defaultInterceptor)
                                                    )
                                                    .processor(psepModuleOne)
                                                    .processor(psepModuleTwo)
            ));

            AxonConfiguration configuration = configurer.build();
            configuration.start();

            // When publishing a String event
            EventMessage stringEvent = EventTestUtils.asEventMessage("test-event");
            eventSource.publishMessage(stringEvent);

            // Then only component one handles it
            await().atMost(Duration.ofMillis(500))
                   .untilAsserted(() -> {
                       assertThat(componentOne.handled(stringEvent)).isTrue();
                       assertThat(componentTwo.handled(stringEvent)).isFalse();
                   });
            assertThat(invokedGlobal.get()).isEqualTo(1);
            assertThat(invokedDefault.get()).isEqualTo(1);
            assertThat(invokedSpecificOne).isTrue();
            assertThat(invokedSpecificTwo).isFalse();
            // Reset invoked interceptor flag
            invokedSpecificOne.set(false);

            // When publishing an Integer event
            EventMessage integerEvent = EventTestUtils.asEventMessage(42);
            eventSource.publishMessage(integerEvent);

            // Then only component two handles it
            await().atMost(Duration.ofMillis(500))
                   .untilAsserted(() -> {
                       assertThat(componentOne.handled(integerEvent)).isFalse();
                       assertThat(componentTwo.handled(integerEvent)).isTrue();
                   });
            assertThat(invokedGlobal.get()).isEqualTo(2);
            assertThat(invokedDefault.get()).isEqualTo(2);
            assertThat(invokedSpecificOne).isFalse();
            assertThat(invokedSpecificTwo).isTrue();

            // Clean-up
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

        @Test
        void shouldRegisterTokenStoreAsComponent() {
            // given
            var processorName = "testProcessor";
            var tokenStore = new InMemoryTokenStore();
            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(singleTestEventHandlingComponent())
                    .customized((cfg, c) -> c.eventSource(new AsyncInMemoryStreamableEventSource())
                                             .tokenStore(tokenStore));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var registeredTokenStore =
                    configuration.getModuleConfiguration(processorName)
                                 .flatMap(m -> m.getOptionalComponent(
                                         TokenStore.class,
                                         "TokenStore[" + processorName + "]"
                                 ));

            // then
            assertThat(registeredTokenStore).isPresent();
            assertThat(registeredTokenStore.get()).isSameAs(tokenStore);
        }

        @Test
        void shouldRegisterUnitOfWorkFactoryAsComponent() {
            // given
            var processorName = "testProcessor";
            var unitOfWorkFactory = new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE);
            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(singleTestEventHandlingComponent())
                    .customized((cfg, c) -> c.eventSource(new AsyncInMemoryStreamableEventSource())
                                             .tokenStore(new InMemoryTokenStore())
                                             .unitOfWorkFactory(unitOfWorkFactory));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var registeredUnitOfWorkFactory =
                    configuration.getModuleConfiguration(processorName)
                                 .flatMap(m -> m.getOptionalComponent(
                                         UnitOfWorkFactory.class,
                                         "UnitOfWorkFactory[" + processorName + "]"
                                 ));

            // then
            assertThat(registeredUnitOfWorkFactory).isPresent();
            assertThat(registeredUnitOfWorkFactory.get()).isSameAs(unitOfWorkFactory);
        }

        @Test
        void shouldRegisterEventHandlingComponentsAsComponents() {
            // given
            var processorName = "testProcessor";
            var component1 = SimpleEventHandlingComponent.create("component1");
            component1.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());
            var component2 = SimpleEventHandlingComponent.create("component2");
            component2.subscribe(new QualifiedName(Integer.class), (event, context) -> MessageStream.empty());
            var component3 = SimpleEventHandlingComponent.create("component3");
            component3.subscribe(new QualifiedName(Long.class), (event, context) -> MessageStream.empty());

            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.declarative(cfg -> component1)
                                                                     .declarative(cfg -> component2)
                                                                     .declarative(cfg -> component3))
                    .customized((cfg, c) -> c.eventSource(new AsyncInMemoryStreamableEventSource()));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var registeredComponent1 =
                    configuration.getModuleConfiguration(processorName)
                                 .flatMap(m -> m.getOptionalComponent(
                                         EventHandlingComponent.class,
                                         "EventHandlingComponent[" + processorName + "][0]"
                                 ));
            var registeredComponent2 =
                    configuration.getModuleConfiguration(processorName)
                                 .flatMap(m -> m.getOptionalComponent(
                                         EventHandlingComponent.class,
                                         "EventHandlingComponent[" + processorName + "][1]"
                                 ));
            var registeredComponent3 =
                    configuration.getModuleConfiguration(processorName)
                                 .flatMap(m -> m.getOptionalComponent(
                                         EventHandlingComponent.class,
                                         "EventHandlingComponent[" + processorName + "][2]"
                                 ));

            // then
            assertThat(registeredComponent1).isPresent();
            assertThat(registeredComponent2).isPresent();
            assertThat(registeredComponent3).isPresent();
        }

        @Test
        void shouldWrapEventHandlingComponentsWithDeadLetterProcessorWhenDlqConfigured() {
            // given
            var processorName = "testProcessor";
            var component = SimpleEventHandlingComponent.create("component");
            component.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());

            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.declarative(cfg -> component))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .deadLetterQueue(DeadLetterQueueConfiguration::enabled));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var registeredComponent = configuration.getModuleConfiguration(processorName)
                                                   .flatMap(m -> m.getOptionalComponent(EventHandlingComponent.class,
                                                                                         "EventHandlingComponent[" + processorName + "][0]"));

            // then
            assertThat(registeredComponent).isPresent();
            assertThat(registeredComponent.get()).isInstanceOf(SequencedDeadLetterProcessor.class);
        }

        @Test
        void shouldWrapAllEventHandlingComponentsWithDeadLetterProcessorWhenDlqConfigured() {
            // given
            var processorName = "testProcessor";
            var component0 = SimpleEventHandlingComponent.create("component0");
            component0.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());
            var component1 = SimpleEventHandlingComponent.create("component1");
            component1.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());

            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.declarative(cfg -> component0).declarative(cfg -> component1))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .deadLetterQueue(DeadLetterQueueConfiguration::enabled));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var registeredComponents = configuration.getModuleConfiguration(processorName)
                                                   .map(m -> m.getComponents(EventHandlingComponent.class));

            // then
            assertThat(registeredComponents).isPresent();
            assertThat(registeredComponents.get().values()).allSatisfy(c -> assertThat(c).isInstanceOf(SequencedDeadLetterProcessor.class));
        }

        @Test
        void shouldRegisterAllSequencedDeadLetterProcessorsWhenDlqConfigured() {
            // given
            var processorName = "testProcessor";
            var component0 = SimpleEventHandlingComponent.create("component0");
            component0.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());
            var component1 = SimpleEventHandlingComponent.create("component1");
            component1.subscribe(new QualifiedName(Integer.class), (event, context) -> MessageStream.empty());

            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.declarative(cfg -> component0).declarative(cfg -> component1))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .deadLetterQueue(DeadLetterQueueConfiguration::enabled));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var deadLetterProcessors = configuration.getModuleConfiguration(processorName)
                                                    .map(m -> m.getComponents(SequencedDeadLetterProcessor.class));

            // then
            assertThat(deadLetterProcessors).isPresent();
            assertThat(deadLetterProcessors.get()).hasSize(2);
            assertThat(deadLetterProcessors.get().values()).allSatisfy(
                    dlp -> assertThat(dlp).isInstanceOf(SequencedDeadLetterProcessor.class)
            );
        }

        @Test
        void shouldNotRegisterSequencedDeadLetterProcessorsWhenDlqNotConfigured() {
            // given
            var processorName = "testProcessor";
            var component = SimpleEventHandlingComponent.create("component");
            component.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());

            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.declarative(cfg -> component))
                    .customized((cfg, c) -> c.eventSource(new AsyncInMemoryStreamableEventSource()));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var deadLetterProcessors = configuration.getModuleConfiguration(processorName)
                                                    .map(m -> m.getComponents(SequencedDeadLetterProcessor.class));

            // then
            assertThat(deadLetterProcessors).isPresent();
            assertThat(deadLetterProcessors.get().values()).allMatch(java.util.Objects::isNull);
        }

        @Test
        void shouldRetrieveAllDeadLetterProcessorsFromRootConfigurationAcrossAllModules() {
            // given
            var processor1Name = "processor1";
            var processor2Name = "processor2";

            var component1 = SimpleEventHandlingComponent.create("component1");
            component1.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());
            var component2 = SimpleEventHandlingComponent.create("component2");
            component2.subscribe(new QualifiedName(Integer.class), (event, context) -> MessageStream.empty());

            var module1 = EventProcessorModule
                    .pooledStreaming(processor1Name)
                    .eventHandlingComponents(components -> components.declarative(cfg -> component1))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .deadLetterQueue(DeadLetterQueueConfiguration::enabled));

            var module2 = EventProcessorModule
                    .pooledStreaming(processor2Name)
                    .eventHandlingComponents(components -> components.declarative(cfg -> component2))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .deadLetterQueue(DeadLetterQueueConfiguration::enabled));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .processor(module1)
                    .processor(module2)));
            var configuration = configurer.build();

            // when - get all processors from root configuration (should search all modules)
            var allDeadLetterProcessors = configuration.getComponents(SequencedDeadLetterProcessor.class);

            // then - should find processors from both modules
            assertThat(allDeadLetterProcessors).hasSize(2);
            assertThat(allDeadLetterProcessors.values()).allSatisfy(
                    dlp -> assertThat(dlp).isInstanceOf(SequencedDeadLetterProcessor.class)
            );
        }

        @Test
        void shouldNotWrapEventHandlingComponentsWithDeadLetterProcessorWhenDlqNotConfigured() {
            // given
            var processorName = "testProcessor";
            var component = SimpleEventHandlingComponent.create("component");
            component.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());

            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.declarative(cfg -> component))
                    .customized((cfg, c) -> c.eventSource(new AsyncInMemoryStreamableEventSource()));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var registeredComponent = configuration.getModuleConfiguration(processorName)
                                                   .flatMap(m -> m.getOptionalComponent(EventHandlingComponent.class,
                                                                                         "EventHandlingComponent[" + processorName + "][0]"));

            // then
            assertThat(registeredComponent).isPresent();
            assertThat(registeredComponent.get()).isNotInstanceOf(SequencedDeadLetterProcessor.class);
        }

        @Test
        void shouldNotShareSequencedDeadLetterQueueBetweenEventHandlingComponentsInSingleProcessor() {
            // given
            var processorName = "testProcessor";
            var component0 = SimpleEventHandlingComponent.create("component0");
            component0.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());
            var component1 = SimpleEventHandlingComponent.create("component1");
            component1.subscribe(new QualifiedName(Integer.class), (event, context) -> MessageStream.empty());

            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.declarative(cfg -> component0).declarative(cfg -> component1))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .deadLetterQueue(DeadLetterQueueConfiguration::enabled));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var dlq0 = configuration.getModuleConfiguration(processorName)
                                    .flatMap(m -> m.getOptionalComponent(
                                            CachingSequencedDeadLetterQueue.class,
                                            "CachingDeadLetterQueue[" + processorName + "][0]"
                                    ));
            var dlq1 = configuration.getModuleConfiguration(processorName)
                                    .flatMap(m -> m.getOptionalComponent(
                                            CachingSequencedDeadLetterQueue.class,
                                            "CachingDeadLetterQueue[" + processorName + "][1]"
                                    ));

            // then
            assertThat(dlq0).isPresent();
            assertThat(dlq1).isPresent();
            assertThat(dlq0.get()).isNotSameAs(dlq1.get());
        }

        @Test
        void shouldUseCustomDlqFactoryForProcessorWithMultipleEventHandlingComponents() {
            // given
            var processorName = "testProcessor";
            var component0 = SimpleEventHandlingComponent.create("component0");
            component0.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());
            var component1 = SimpleEventHandlingComponent.create("component1");
            component1.subscribe(new QualifiedName(Integer.class), (event, context) -> MessageStream.empty());
            var component2 = SimpleEventHandlingComponent.create("component2");
            component2.subscribe(new QualifiedName(Long.class), (event, context) -> MessageStream.empty());

            // and - custom factory that tracks created queues
            Map<String, SequencedDeadLetterQueue<EventMessage>> createdQueues = new ConcurrentHashMap<>();
            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components
                            .declarative(cfg -> component0)
                            .declarative(cfg -> component1)
                            .declarative(cfg -> component2))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .deadLetterQueue(dlq -> dlq
                                    .enabled()
                                    .factory(name -> {
                                        SequencedDeadLetterQueue<EventMessage> queue =
                                                InMemorySequencedDeadLetterQueue.defaultQueue();
                                        createdQueues.put(name, queue);
                                        return queue;
                                    })));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var dlq0 = configuration.getModuleConfiguration(processorName)
                                    .flatMap(m -> m.getOptionalComponent(
                                            CachingSequencedDeadLetterQueue.class,
                                            "CachingDeadLetterQueue[" + processorName + "][0]"
                                    ));
            var dlq1 = configuration.getModuleConfiguration(processorName)
                                    .flatMap(m -> m.getOptionalComponent(
                                            CachingSequencedDeadLetterQueue.class,
                                            "CachingDeadLetterQueue[" + processorName + "][1]"
                                    ));
            var dlq2 = configuration.getModuleConfiguration(processorName)
                                    .flatMap(m -> m.getOptionalComponent(
                                            CachingSequencedDeadLetterQueue.class,
                                            "CachingDeadLetterQueue[" + processorName + "][2]"
                                    ));

            // then - all DLQs are present
            assertThat(dlq0).isPresent();
            assertThat(dlq1).isPresent();
            assertThat(dlq2).isPresent();

            // and - custom factory was used for each component
            assertThat(createdQueues).hasSize(3);
            assertThat(createdQueues).containsKey("DeadLetterQueue[" + processorName + "][0]");
            assertThat(createdQueues).containsKey("DeadLetterQueue[" + processorName + "][1]");
            assertThat(createdQueues).containsKey("DeadLetterQueue[" + processorName + "][2]");
        }

        @Test
        void shouldUseCustomDlqFactoryFromDefaults() {
            // given
            var processorName = "testProcessor";
            var component0 = SimpleEventHandlingComponent.create("component0");
            component0.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());
            var component1 = SimpleEventHandlingComponent.create("component1");
            component1.subscribe(new QualifiedName(Integer.class), (event, context) -> MessageStream.empty());

            // and - custom factory that tracks created queues
            Map<String, SequencedDeadLetterQueue<EventMessage>> createdQueues = new ConcurrentHashMap<>();
            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components
                            .declarative(cfg -> component0)
                            .declarative(cfg -> component1))
                    .customized((cfg, c) -> c.deadLetterQueue(DeadLetterQueueConfiguration::enabled));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .deadLetterQueue(dlq -> dlq.factory(name -> {
                                SequencedDeadLetterQueue<EventMessage> queue =
                                        InMemorySequencedDeadLetterQueue.defaultQueue();
                                createdQueues.put(name, queue);
                                return queue;
                            })))
                    .processor(module)));
            var configuration = configurer.build();

            // when
            var dlq0 = configuration.getModuleConfiguration(processorName)
                                    .flatMap(m -> m.getOptionalComponent(
                                            CachingSequencedDeadLetterQueue.class,
                                            "CachingDeadLetterQueue[" + processorName + "][0]"
                                    ));
            var dlq1 = configuration.getModuleConfiguration(processorName)
                                    .flatMap(m -> m.getOptionalComponent(
                                            CachingSequencedDeadLetterQueue.class,
                                            "CachingDeadLetterQueue[" + processorName + "][1]"
                                    ));

            // then - all DLQs are present
            assertThat(dlq0).isPresent();
            assertThat(dlq1).isPresent();

            // and - custom factory from defaults was used for each component
            assertThat(createdQueues).hasSize(2);
            assertThat(createdQueues).containsKey("DeadLetterQueue[" + processorName + "][0]");
            assertThat(createdQueues).containsKey("DeadLetterQueue[" + processorName + "][1]");
        }

        @Test
        @DisplayName("Processor can disable DLQ even when enabled in defaults")
        void shouldDisableDlqForProcessorEvenWhenEnabledInDefaults() {
            // given - DLQ is enabled in defaults
            var processorWithDlq = "processorWithDlq";
            var processorWithoutDlq = "processorWithoutDlq";

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .deadLetterQueue(dlq -> dlq.enabled()))
                    // Processor that keeps DLQ enabled (default from defaults)
                    .processor(EventProcessorModule
                                       .pooledStreaming(processorWithDlq)
                                       .eventHandlingComponents(singleTestEventHandlingComponent())
                                       .notCustomized())
                    // Processor that explicitly disables DLQ
                    .processor(EventProcessorModule
                                       .pooledStreaming(processorWithoutDlq)
                                       .eventHandlingComponents(singleTestEventHandlingComponent())
                                       .customized((cfg, c) -> c.deadLetterQueue(dlq -> dlq.disabled())))
            ));

            // when
            var configuration = configurer.build();

            // then - processorWithDlq should have DLQ
            var dlqEnabled = configuration.getModuleConfiguration(processorWithDlq)
                                          .flatMap(m -> m.getOptionalComponent(
                                                  CachingSequencedDeadLetterQueue.class,
                                                  "CachingDeadLetterQueue[" + processorWithDlq + "][0]"
                                          ));
            assertThat(dlqEnabled).isPresent();

            // and - processorWithoutDlq should NOT have DLQ (disabled overrides enabled from defaults)
            var dlqDisabled = configuration.getModuleConfiguration(processorWithoutDlq)
                                           .flatMap(m -> m.getOptionalComponent(
                                                   CachingSequencedDeadLetterQueue.class,
                                                   "CachingDeadLetterQueue[" + processorWithoutDlq + "][0]"
                                           ));
            assertThat(dlqDisabled).isEmpty();
        }
    }

    @Nested
    @DisplayName("Configuration Hierarchy: Should apply configuration customizations in order: shared -> type-specific -> instance-specific")
    class ConfigurationHierarchyTest {

        @Test
        @DisplayName("Case #1: EventProcessorModule - customized")
        void moduleCustomizedConfiguration() {
            // given
            var configurer = MessagingConfigurer.create();
            var processorName = "testProcessor";

            // and - shared customization
            UnitOfWorkFactory sharedUnitOfWorkFactory = new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE);
            ErrorHandler sharedErrorHandler = PropagatingErrorHandler.instance();
            configurer.eventProcessing(ep -> ep.defaults(d -> d.errorHandler(sharedErrorHandler)
                                                               .unitOfWorkFactory(sharedUnitOfWorkFactory)));

            // and - type-specific customization
            UnitOfWorkFactory typeUnitOfWorkFactory = aTransactionalUnitOfWork();
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
        void moduleCustomizedWithNewConfiguration() {
            // given
            var configurer = MessagingConfigurer.create();
            var processorName = "testProcessor";

            // and - shared customization
            UnitOfWorkFactory sharedUnitOfWorkFactory = new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE);
            ErrorHandler sharedErrorHandler = PropagatingErrorHandler.instance();
            configurer.eventProcessing(ep -> ep.defaults(d -> d.errorHandler(sharedErrorHandler)
                                                               .unitOfWorkFactory(sharedUnitOfWorkFactory)));

            // and - type-specific customization
            UnitOfWorkFactory typeUnitOfWorkFactory = aTransactionalUnitOfWork();
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
        void moduleWithTypeSpecificConfiguration() {
            // given
            var configurer = MessagingConfigurer.create();
            var processorName = "testProcessor";

            // and - shared customization
            UnitOfWorkFactory sharedUnitOfWorkFactory = new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE);
            ErrorHandler sharedErrorHandler = PropagatingErrorHandler.instance();
            configurer.eventProcessing(ep -> ep.defaults(d -> d.errorHandler(sharedErrorHandler)
                                                               .unitOfWorkFactory(sharedUnitOfWorkFactory)));

            // and - type-specific customization
            UnitOfWorkFactory typeUnitOfWorkFactory = aTransactionalUnitOfWork();
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
        void moduleWithGlobalEventSource() {
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
        void defaultsDoNotOverrideGlobalComponents() {
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
        void defaultsDoNotRegisterGlobalComponents() {
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
            var component = SimpleEventHandlingComponent.create("test");
            var customCoordinator = new AtomicReference<ScheduledExecutorService>();
            var customWorker = new AtomicReference<ScheduledExecutorService>();

            //  when
            PooledStreamingEventProcessorModule module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents((components) -> components.declarative(cfg -> component))
                    .customized((cfg, processorConfig) -> processorConfig
                            .coordinatorExecutor(customCoordinator.updateAndGet(e -> Executors.newScheduledThreadPool(1)))
                            .workerExecutor(customWorker.updateAndGet(e -> Executors.newScheduledThreadPool(2))));

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
        return simpleRecordingTestComponent(new QualifiedName(String.class));
    }

    private static RecordingEventHandlingComponent simpleRecordingTestComponent(
            @Nonnull QualifiedName supportedEventName
    ) {
        return new RecordingEventHandlingComponent(
                SimpleEventHandlingComponent.create("test")
                                            .subscribe(supportedEventName, (e, c) -> MessageStream.empty())
        );
    }

    private PooledStreamingEventProcessorModule minimalProcessorModule(String processorName) {
        return EventProcessorModule
                .pooledStreaming(processorName)
                .eventHandlingComponents(singleTestEventHandlingComponent())
                .customized((cfg, c) -> c.eventSource(cfg.getOptionalComponent(StreamableEventSource.class)
                                                         .orElse(new AsyncInMemoryStreamableEventSource())));
    }

    @Nonnull
    private static Function<EventHandlingComponentsConfigurer.RequiredComponentPhase, EventHandlingComponentsConfigurer.CompletePhase> singleTestEventHandlingComponent() {
        var eventHandlingComponent = SimpleEventHandlingComponent.create("test");
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

    @Nonnull
    private static TransactionalUnitOfWorkFactory aTransactionalUnitOfWork() {
        return UnitOfWorkTestUtils.transactionalUnitOfWorkFactory(new NoOpTransactionManager());
    }
}