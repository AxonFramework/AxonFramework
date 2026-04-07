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

import org.jspecify.annotations.NonNull;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.deadletter.InMemorySequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.AsyncInMemoryStreamableEventSource;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.axonframework.messaging.eventhandling.deadletter.DeadLetterQueueConfiguration;
import org.axonframework.messaging.eventhandling.deadletter.DeadLetteringEventHandlingComponent;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating dead letter queue configuration and decoration within the
 * {@link PooledStreamingEventProcessorModule}.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
class PooledStreamingEventProcessorModuleDeadLetterQueueTest {

    @Nested
    class ComponentRegistrationTest {

        @Test
        void shouldWrapEventHandlingComponentsWithDeadLetterProcessorWhenDlqConfigured() {
            // given
            var processorName = "testProcessor";
            var component = SimpleEventHandlingComponent.create("component");
            component.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());

            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.declarative("component", cfg -> component))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .extend(DeadLetterQueueConfiguration.class, () -> new DeadLetterQueueConfiguration().enabled()));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var registeredComponent = configuration.getModuleConfiguration(processorName)
                                                   .flatMap(m -> m.getOptionalComponent(EventHandlingComponent.class,
                                                                                        "EventHandlingComponent[" + processorName + "][component]"));

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
                    .eventHandlingComponents(components -> components.declarative("component0", cfg -> component0).declarative("component1", cfg -> component1))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .extend(DeadLetterQueueConfiguration.class, () -> new DeadLetterQueueConfiguration().enabled()));

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
        void shouldResolveSequencedDeadLetterProcessorsViaFactoryWhenDlqConfigured() {
            // given
            var processorName = "testProcessor";
            var component0 = SimpleEventHandlingComponent.create("component0");
            component0.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());
            var component1 = SimpleEventHandlingComponent.create("component1");
            component1.subscribe(new QualifiedName(Integer.class), (event, context) -> MessageStream.empty());

            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.declarative("component0", cfg -> component0).declarative("component1", cfg -> component1))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .extend(DeadLetterQueueConfiguration.class, () -> new DeadLetterQueueConfiguration().enabled()));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var moduleConfig = configuration.getModuleConfiguration(processorName);
            var dlp0 = moduleConfig.flatMap(m -> m.getOptionalComponent(
                    SequencedDeadLetterProcessor.class,
                    "EventHandlingComponent[" + processorName + "][component0]"
            ));
            var dlp1 = moduleConfig.flatMap(m -> m.getOptionalComponent(
                    SequencedDeadLetterProcessor.class,
                    "EventHandlingComponent[" + processorName + "][component1]"
            ));

            // then
            assertThat(dlp0).isPresent();
            assertThat(dlp1).isPresent();
        }

        @Test
        void shouldNotResolveSequencedDeadLetterProcessorsWhenDlqNotConfigured() {
            // given
            var processorName = "testProcessor";
            var component = SimpleEventHandlingComponent.create("component");
            component.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());

            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.declarative("component", cfg -> component))
                    .customized((cfg, c) -> c.eventSource(new AsyncInMemoryStreamableEventSource()));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var dlp = configuration.getModuleConfiguration(processorName)
                                   .flatMap(m -> m.getOptionalComponent(
                                           SequencedDeadLetterProcessor.class,
                                           "EventHandlingComponent[" + processorName + "][component]"
                                   ));

            // then
            assertThat(dlp).isEmpty();
        }

        @Test
        void shouldResolveDeadLetterProcessorsAcrossMultipleModules() {
            // given
            var processor1Name = "processor1";
            var processor2Name = "processor2";

            var component1 = SimpleEventHandlingComponent.create("component1");
            component1.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());
            var component2 = SimpleEventHandlingComponent.create("component2");
            component2.subscribe(new QualifiedName(Integer.class), (event, context) -> MessageStream.empty());

            var module1 = EventProcessorModule
                    .pooledStreaming(processor1Name)
                    .eventHandlingComponents(components -> components.declarative("component1", cfg -> component1))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .extend(DeadLetterQueueConfiguration.class, () -> new DeadLetterQueueConfiguration().enabled()));

            var module2 = EventProcessorModule
                    .pooledStreaming(processor2Name)
                    .eventHandlingComponents(components -> components.declarative("component2", cfg -> component2))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .extend(DeadLetterQueueConfiguration.class, () -> new DeadLetterQueueConfiguration().enabled()));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .processor(module1)
                    .processor(module2)));
            var configuration = configurer.build();

            // when - resolve dead letter processors from each module
            var dlp1 = configuration.getModuleConfiguration(processor1Name)
                                    .flatMap(m -> m.getOptionalComponent(
                                            SequencedDeadLetterProcessor.class,
                                            "EventHandlingComponent[" + processor1Name + "][component1]"
                                    ));
            var dlp2 = configuration.getModuleConfiguration(processor2Name)
                                    .flatMap(m -> m.getOptionalComponent(
                                            SequencedDeadLetterProcessor.class,
                                            "EventHandlingComponent[" + processor2Name + "][component2]"
                                    ));

            // then - should find processors from both modules
            assertThat(dlp1).isPresent();
            assertThat(dlp2).isPresent();
        }

        @Test
        void shouldNotWrapEventHandlingComponentsWithDeadLetterProcessorWhenDlqNotConfigured() {
            // given
            var processorName = "testProcessor";
            var component = SimpleEventHandlingComponent.create("component");
            component.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());

            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.declarative("component", cfg -> component))
                    .customized((cfg, c) -> c.eventSource(new AsyncInMemoryStreamableEventSource()));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var registeredComponent = configuration.getModuleConfiguration(processorName)
                                                   .flatMap(m -> m.getOptionalComponent(EventHandlingComponent.class,
                                                                                        "EventHandlingComponent[" + processorName + "][component]"));

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
                    .eventHandlingComponents(components -> components.declarative("component0", cfg -> component0).declarative("component1", cfg -> component1))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .extend(DeadLetterQueueConfiguration.class, () -> new DeadLetterQueueConfiguration().enabled()));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var dlq0 = configuration.getModuleConfiguration(processorName)
                                    .flatMap(m -> m.getOptionalComponent(
                                            SequencedDeadLetterQueue.class,
                                            dlqName(processorName, "component0")
                                    ));
            var dlq1 = configuration.getModuleConfiguration(processorName)
                                    .flatMap(m -> m.getOptionalComponent(
                                            SequencedDeadLetterQueue.class,
                                            dlqName(processorName, "component1")
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
                            .declarative("component0", cfg -> component0)
                            .declarative("component1", cfg -> component1)
                            .declarative("component2", cfg -> component2))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .extend(DeadLetterQueueConfiguration.class, () -> new DeadLetterQueueConfiguration()
                                    .enabled()
                                    .factory((name, ignored) -> {
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
                                            SequencedDeadLetterQueue.class,
                                            dlqName(processorName, "component0")
                                    ));
            var dlq1 = configuration.getModuleConfiguration(processorName)
                                    .flatMap(m -> m.getOptionalComponent(
                                            SequencedDeadLetterQueue.class,
                                            dlqName(processorName, "component1")
                                    ));
            var dlq2 = configuration.getModuleConfiguration(processorName)
                                    .flatMap(m -> m.getOptionalComponent(
                                            SequencedDeadLetterQueue.class,
                                            dlqName(processorName, "component2")
                                    ));

            // then - all DLQs are present
            assertThat(dlq0).isPresent();
            assertThat(dlq1).isPresent();
            assertThat(dlq2).isPresent();

            // and - custom factory was used for each component
            assertThat(createdQueues).hasSize(3);
            assertThat(createdQueues).containsKey(dlqName(processorName, "component0"));
            assertThat(createdQueues).containsKey(dlqName(processorName, "component1"));
            assertThat(createdQueues).containsKey(dlqName(processorName, "component2"));
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
                            .declarative("component0", cfg -> component0)
                            .declarative("component1", cfg -> component1))
                    .notCustomized();

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .extend(DeadLetterQueueConfiguration.class, () -> new DeadLetterQueueConfiguration()
                                    .enabled()
                                    .factory((name, ignored) -> {
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
                                            SequencedDeadLetterQueue.class,
                                            dlqName(processorName, "component0")
                                    ));
            var dlq1 = configuration.getModuleConfiguration(processorName)
                                    .flatMap(m -> m.getOptionalComponent(
                                            SequencedDeadLetterQueue.class,
                                            dlqName(processorName, "component1")
                                    ));

            // then - all DLQs are present
            assertThat(dlq0).isPresent();
            assertThat(dlq1).isPresent();

            // and - custom factory from defaults was used for each component
            assertThat(createdQueues).hasSize(2);
            assertThat(createdQueues).containsKey(dlqName(processorName, "component0"));
            assertThat(createdQueues).containsKey(dlqName(processorName, "component1"));
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
                            .extend(DeadLetterQueueConfiguration.class, () -> new DeadLetterQueueConfiguration().enabled()))
                    // Processor that keeps DLQ enabled (default from defaults)
                    .processor(EventProcessorModule
                                       .pooledStreaming(processorWithDlq)
                                       .eventHandlingComponents(singleTestEventHandlingComponent())
                                       .notCustomized())
                    // Processor that explicitly disables DLQ
                    .processor(EventProcessorModule
                                       .pooledStreaming(processorWithoutDlq)
                                       .eventHandlingComponents(singleTestEventHandlingComponent())
                                       .customized((cfg, c) -> c.extend(DeadLetterQueueConfiguration.class, () -> new DeadLetterQueueConfiguration().disabled())))
            ));

            // when
            var configuration = configurer.build();

            // then - processorWithDlq should have DLQ
            var dlqEnabled = configuration.getModuleConfiguration(processorWithDlq)
                                          .flatMap(m -> m.getOptionalComponent(
                                                  SequencedDeadLetterQueue.class,
                                                  dlqName(processorWithDlq, "eventHandlingComponent")
                                          ));
            assertThat(dlqEnabled).isPresent();

            // and - processorWithoutDlq should NOT have DLQ (disabled overrides enabled from defaults)
            var dlqDisabled = configuration.getModuleConfiguration(processorWithoutDlq)
                                           .flatMap(m -> m.getOptionalComponent(
                                                   SequencedDeadLetterQueue.class,
                                                   dlqName(processorWithoutDlq, "eventHandlingComponent")
                                           ));
            assertThat(dlqDisabled).isEmpty();
        }
    }

    @Nested
    class DecorationTest {

        @Test
        void shouldDecorateEventHandlingComponentWhenDlqEnabled() {
            // given
            var processorName = "testProcessor";
            var component = SimpleEventHandlingComponent.create("component");
            component.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());

            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.declarative("component", cfg -> component))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .extend(DeadLetterQueueConfiguration.class, () -> new DeadLetterQueueConfiguration().enabled()));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var registeredComponent = configuration.getModuleConfiguration(processorName)
                                                   .flatMap(m -> m.getOptionalComponent(
                                                           EventHandlingComponent.class,
                                                           "EventHandlingComponent[" + processorName + "][component]"
                                                   ));

            // then
            assertThat(registeredComponent).isPresent().get()
                                           .isInstanceOf(DeadLetteringEventHandlingComponent.class)
                                           .isInstanceOf(SequencedDeadLetterProcessor.class);
        }

        @Test
        void shouldNotDecorateEventHandlingComponentWhenDlqDisabled() {
            // given
            var processorName = "testProcessor";
            var component = SimpleEventHandlingComponent.create("component");
            component.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());

            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.declarative("component", cfg -> component))
                    .customized((cfg, c) -> c.eventSource(new AsyncInMemoryStreamableEventSource()));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var registeredComponent = configuration.getModuleConfiguration(processorName)
                                                   .flatMap(m -> m.getOptionalComponent(
                                                           EventHandlingComponent.class,
                                                           "EventHandlingComponent[" + processorName + "][component]"
                                                   ));

            // then
            assertThat(registeredComponent).isPresent().get()
                                           .isNotInstanceOf(DeadLetteringEventHandlingComponent.class)
                                           .isNotInstanceOf(SequencedDeadLetterProcessor.class);
        }

        @Test
        void shouldDecorateAllEventHandlingComponentsWhenDlqEnabled() {
            // given
            var processorName = "testProcessor";
            var component0 = SimpleEventHandlingComponent.create("component0");
            component0.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());
            var component1 = SimpleEventHandlingComponent.create("component1");
            component1.subscribe(new QualifiedName(Integer.class), (event, context) -> MessageStream.empty());

            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components
                            .declarative("component0", cfg -> component0)
                            .declarative("component1", cfg -> component1))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .extend(DeadLetterQueueConfiguration.class, () -> new DeadLetterQueueConfiguration().enabled()));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();

            // when
            var moduleConfig = configuration.getModuleConfiguration(processorName);
            var ehc0 = moduleConfig.flatMap(m -> m.getOptionalComponent(
                    EventHandlingComponent.class,
                    "EventHandlingComponent[" + processorName + "][component0]"
            ));
            var ehc1 = moduleConfig.flatMap(m -> m.getOptionalComponent(
                    EventHandlingComponent.class,
                    "EventHandlingComponent[" + processorName + "][component1]"
            ));

            // then
            assertThat(ehc0).isPresent().get()
                            .isInstanceOf(DeadLetteringEventHandlingComponent.class)
                            .isInstanceOf(SequencedDeadLetterProcessor.class);
            assertThat(ehc1).isPresent().get()
                            .isInstanceOf(DeadLetteringEventHandlingComponent.class)
                            .isInstanceOf(SequencedDeadLetterProcessor.class);
        }

        @Test
        void decorationCreatesDlqViaFactoryAndSubsequentLookupReturnsSameInstance() {
            // given
            Map<String, SequencedDeadLetterQueue<EventMessage>> createdQueues = new ConcurrentHashMap<>();
            var processorName = "testProcessor";
            var component = SimpleEventHandlingComponent.create("component");
            component.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());

            var module = EventProcessorModule
                    .pooledStreaming(processorName)
                    .eventHandlingComponents(components -> components.declarative("component", cfg -> component))
                    .customized((cfg, c) -> c
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .extend(DeadLetterQueueConfiguration.class, () -> new DeadLetterQueueConfiguration()
                                    .enabled()
                                    .factory((name, ignored) -> {
                                        var queue = InMemorySequencedDeadLetterQueue.<EventMessage>defaultQueue();
                                        createdQueues.put(name, queue);
                                        return queue;
                                    })));

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(module)));
            var configuration = configurer.build();
            var moduleConfig = configuration.getModuleConfiguration(processorName);

            // when - resolve EventHandlingComponent (triggers lazy decoration, which creates DLQ via factory)
            moduleConfig.flatMap(m -> m.getOptionalComponent(
                    EventHandlingComponent.class,
                    "EventHandlingComponent[" + processorName + "][component]"
            ));

            // then - factory was called exactly once
            assertThat(createdQueues).hasSize(1);
            assertThat(createdQueues).containsKey(dlqName(processorName, "component"));

            // when - look up DLQ by name
            moduleConfig.flatMap(m -> m.getOptionalComponent(
                    SequencedDeadLetterQueue.class,
                    dlqName(processorName, "component")
            ));

            // then - factory was NOT called again (DLQ was already created by the decorator)
            assertThat(createdQueues).hasSize(1);
        }

        @Test
        void shouldDecorateWhenDlqEnabledInDefaultsAndNotOverridden() {
            // given
            var processorWithDlq = "processorWithDlq";
            var processorWithoutDlq = "processorWithoutDlq";

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
                    .defaults(d -> d
                            .eventSource(new AsyncInMemoryStreamableEventSource())
                            .extend(DeadLetterQueueConfiguration.class, () -> new DeadLetterQueueConfiguration().enabled()))
                    .processor(EventProcessorModule
                                       .pooledStreaming(processorWithDlq)
                                       .eventHandlingComponents(singleTestEventHandlingComponent())
                                       .notCustomized())
                    .processor(EventProcessorModule
                                       .pooledStreaming(processorWithoutDlq)
                                       .eventHandlingComponents(singleTestEventHandlingComponent())
                                       .customized((cfg, c) -> c.extend(DeadLetterQueueConfiguration.class,
                                                                        () -> new DeadLetterQueueConfiguration().disabled())))
            ));
            var configuration = configurer.build();

            // when
            var ehcWithDlq = configuration.getModuleConfiguration(processorWithDlq)
                                          .flatMap(m -> m.getOptionalComponent(
                                                  EventHandlingComponent.class,
                                                  "EventHandlingComponent[" + processorWithDlq + "][eventHandlingComponent]"
                                          ));
            var ehcWithoutDlq = configuration.getModuleConfiguration(processorWithoutDlq)
                                             .flatMap(m -> m.getOptionalComponent(
                                                     EventHandlingComponent.class,
                                                     "EventHandlingComponent[" + processorWithoutDlq + "][eventHandlingComponent]"
                                             ));

            // then - enabled processor's component is decorated
            assertThat(ehcWithDlq).isPresent().get()
                                  .isInstanceOf(DeadLetteringEventHandlingComponent.class)
                                  .isInstanceOf(SequencedDeadLetterProcessor.class);

            // and - disabled processor's component is NOT decorated
            assertThat(ehcWithoutDlq).isPresent().get()
                                     .isNotInstanceOf(DeadLetteringEventHandlingComponent.class)
                                     .isNotInstanceOf(SequencedDeadLetterProcessor.class);
        }
    }

    private static String dlqName(String processorName, String componentName) {
        return "DeadLetterQueue[EventHandlingComponent[" + processorName + "][" + componentName + "]]";
    }

    private @NonNull static Function<EventHandlingComponentsConfigurer.RequiredComponentPhase, EventHandlingComponentsConfigurer.CompletePhase> singleTestEventHandlingComponent() {
        var eventHandlingComponent = SimpleEventHandlingComponent.create("test");
        eventHandlingComponent.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());
        return (components) -> components.declarative("eventHandlingComponent", cfg -> eventHandlingComponent);
    }
}
