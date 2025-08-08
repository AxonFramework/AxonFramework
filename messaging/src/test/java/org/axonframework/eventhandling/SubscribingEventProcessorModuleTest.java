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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.transaction.NoOpTransactionManager;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.subscribing.SubscribingEventProcessorModule;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.TransactionalUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;

import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Test class validating the {@link SubscribingEventProcessorModule} functionality and its integration with the
 * {@link MessagingConfigurer}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class SubscribingEventProcessorModuleTest {

    @Nested
    class ConstructionTest {

        @Test
        void shouldCreateModuleWithProcessorName() {
            // given
            String processorName = "test-processor";

            // when
            SubscribingEventProcessorModule module = EventProcessorModule
                    .subscribing(processorName)
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
            SubscribingEventProcessorModule module = minimalProcessorModule(processorName);

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
            SubscribingEventProcessorModule module = minimalProcessorModule(processorName);

            var configurer = MessagingConfigurer.create();
            configurer.eventProcessing(ep -> ep.subscribing(sp -> sp.processor(module)));
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
            SimpleEventBus eventBus = SimpleEventBus.builder().build();

            var configurer = MessagingConfigurer.create();
            RecordingEventHandlingComponent component1 = simpleRecordingTestComponent();
            RecordingEventHandlingComponent component2 = simpleRecordingTestComponent();
            configurer.eventProcessing(
                    ep -> ep.subscribing(
                            sp -> sp.defaults(d -> d.messageSource(eventBus))
                                    .defaultProcessor("test-processor",
                                                      components -> components.declarative(cfg -> component1)
                                                                              .declarative(cfg -> component2))
                    )
            );
            var configuration = configurer.build();
            configuration.start();

            // when
            EventMessage<String> sampleEvent = EventTestUtils.asEventMessage("test-event");
            eventBus.publish(sampleEvent);

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
            configurer.eventProcessing(ep -> ep.subscribing(sp -> sp.processor(module)));
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
            SimpleEventBus typeMessageSource = SimpleEventBus.builder().build();
            EventProcessingStrategy typeProcessingStrategy = DirectEventProcessingStrategy.INSTANCE;
            configurer.eventProcessing(ep ->
                                               ep.subscribing(sp -> sp.defaults(
                                                       d -> d.unitOfWorkFactory(typeUnitOfWorkFactory)
                                                             .messageSource(typeMessageSource)
                                                             .processingStrategy(typeProcessingStrategy))
                                               )
            );

            // and - instance-specific customization
            EventProcessingStrategy instanceProcessingStrategy = (e, p) -> {
            };
            var module = EventProcessorModule
                    .subscribing(processorName)
                    .eventHandlingComponents(singleTestEventHandlingComponent())
                    .customized((__, p) -> p.processingStrategy(instanceProcessingStrategy));
            configurer.eventProcessing(ep -> ep.subscribing(sp -> sp.processor(module)));

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

            assertThat(processorConfig.messageSource()).isEqualTo(typeMessageSource);

            assertThat(processorConfig.processingStrategy()).isNotEqualTo(typeProcessingStrategy);
            assertThat(processorConfig.processingStrategy()).isEqualTo(instanceProcessingStrategy);
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
            SimpleEventBus typeMessageSource = SimpleEventBus.builder().build();
            EventProcessingStrategy typeProcessingStrategy = DirectEventProcessingStrategy.INSTANCE;
            configurer.eventProcessing(ep ->
                                               ep.subscribing(sp -> sp.defaults(
                                                       d -> d.unitOfWorkFactory(typeUnitOfWorkFactory)
                                                             .messageSource(typeMessageSource)
                                                             .processingStrategy(typeProcessingStrategy))
                                               )
            );

            // and - instance-specific customization
            EventProcessingStrategy instanceProcessingStrategy = (e, p) -> {
            };
            var module = EventProcessorModule
                    .subscribing(processorName)
                    .eventHandlingComponents(singleTestEventHandlingComponent())
                    .customized((__, p) -> new SubscribingEventProcessorConfiguration().messageSource(p.messageSource())
                                                                                       .processingStrategy(
                                                                                               instanceProcessingStrategy));
            configurer.eventProcessing(ep -> ep.subscribing(sp -> sp.processor(module)));

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

            assertThat(processorConfig.messageSource()).isEqualTo(typeMessageSource);

            assertThat(processorConfig.processingStrategy()).isNotEqualTo(typeProcessingStrategy);
            assertThat(processorConfig.processingStrategy()).isEqualTo(instanceProcessingStrategy);
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
            SimpleEventBus typeMessageSource = SimpleEventBus.builder().build();
            EventProcessingStrategy typeProcessingStrategy = DirectEventProcessingStrategy.INSTANCE;
            configurer.eventProcessing(ep ->
                                               ep.subscribing(sp -> sp.defaults(
                                                                      d -> d.unitOfWorkFactory(typeUnitOfWorkFactory)
                                                                            .messageSource(typeMessageSource)
                                                                            .processingStrategy(typeProcessingStrategy)
                                                              )
                                               )
            );

            // and - instance-specific customization
            configurer.eventProcessing(ep -> ep.subscribing(sp -> sp.processor(processorName,
                                                                               p -> p.eventHandlingComponents(c -> c.declarative(
                                                                                             cfg -> simpleRecordingTestComponent()))
                                                                                     .notCustomized())));

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
            assertThat(processorConfig.messageSource()).isEqualTo(typeMessageSource);
            assertThat(processorConfig.processingStrategy()).isEqualTo(typeProcessingStrategy);
        }

        @Test
        @DisplayName("Case #4: EventProcessorModule - globally registered message source")
        void testCase4() {
            // given
            var configurer = MessagingConfigurer.create();
            var processorName = "testProcessor";

            SimpleEventBus globalMessageSource = SimpleEventBus.builder().build();
            configurer.componentRegistry(cr -> cr.registerComponent(SubscribableMessageSource.class,
                                                                    cfg -> globalMessageSource));

            // and
            var module = EventProcessorModule
                    .subscribing(processorName)
                    .eventHandlingComponents(singleTestEventHandlingComponent())
                    .notCustomized();
            configurer.eventProcessing(ep -> ep.subscribing(sp -> sp.processor(module)));

            // when
            var configuration = configurer.build();

            // then
            var processor = processor(configuration, processorName);
            assertThat(processor).isPresent();

            // then
            var processorConfig = configurationOf(processor.orElse(null));
            assertThat(processorConfig).isNotNull();
            assertThat(processorConfig.messageSource()).isEqualTo(globalMessageSource);
        }

        @Test
        @DisplayName("Case #5: EventProcessing defaults do not override global components")
        void testCase5() {
            // given
            var configurer = MessagingConfigurer.create();
            var processorName = "testProcessor";

            SimpleEventBus globalMessageSource = SimpleEventBus.builder().build();
            configurer.componentRegistry(cr -> cr.registerComponent(SubscribableMessageSource.class,
                                                                    cfg -> globalMessageSource));

            // and - type-specific customization
            SimpleEventBus typeMessageSource = SimpleEventBus.builder().build();
            configurer.eventProcessing(ep -> ep.subscribing(
                                               sp -> sp.defaults((cfg, d) -> d.messageSource(typeMessageSource))
                                       )
            );

            // and
            @SuppressWarnings("unchecked")
            var module = EventProcessorModule
                    .subscribing(processorName)
                    .eventHandlingComponents(singleTestEventHandlingComponent())
                    .customized((axonConfig, customization) -> customization.messageSource(
                            axonConfig.getComponent(SubscribableMessageSource.class))
                    );
            configurer.eventProcessing(ep -> ep.subscribing(sp -> sp.processor(module)));

            // when
            var configuration = configurer.build();

            // then
            var processor = processor(configuration, processorName);
            assertThat(processor).isPresent();

            // then
            var processorConfig = configurationOf(processor.orElse(null));
            assertThat(processorConfig).isNotNull();
            assertThat(processorConfig.messageSource()).isNotEqualTo(typeMessageSource);
            assertThat(processorConfig.messageSource()).isEqualTo(globalMessageSource);
        }

        @Test
        @DisplayName("Case #6: EventProcessing defaults do not register components on the MessagingConfigurer level")
        void testCase6() {
            // given
            var configurer = MessagingConfigurer.create();

            // and
            ErrorHandler errorHandler = PropagatingErrorHandler.instance();
            configurer.eventProcessing(ep ->
                                               ep.subscribing(sp -> sp.defaults(d -> d.errorHandler(errorHandler)))
            );

            // when
            var configuration = configurer.build();

            // then
            var messageSource = configuration.getOptionalComponent(ErrorHandler.class);
            assertThat(messageSource).isNotPresent();
        }
    }

    private static RecordingEventHandlingComponent simpleRecordingTestComponent() {
        return new RecordingEventHandlingComponent(
                SimpleEventHandlingComponent
                        .builder()
                        .handles(new QualifiedName(String.class), (e, c) -> MessageStream.empty()).build()
        );
    }

    private SubscribingEventProcessorModule minimalProcessorModule(String processorName) {
        //noinspection unchecked
        return EventProcessorModule
                .subscribing(processorName)
                .eventHandlingComponents(singleTestEventHandlingComponent())
                .customized((cfg, c) -> c.messageSource(cfg.getOptionalComponent(SubscribableMessageSource.class)
                                                           .orElse(SimpleEventBus.builder().build())));
    }

    @Nonnull
    private static Function<EventHandlingComponentsConfigurer.RequiredComponentPhase, EventHandlingComponentsConfigurer.CompletePhase> singleTestEventHandlingComponent() {
        var eventHandlingComponent = new SimpleEventHandlingComponent();
        eventHandlingComponent.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.empty());
        return components -> components.declarative(cfg -> eventHandlingComponent);
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
    private static Optional<SubscribingEventProcessor> processor(
            AxonConfiguration configuration,
            String processorName
    ) {
        return configuration.getModuleConfiguration(processorName)
                            .flatMap(m -> m.getOptionalComponent(SubscribingEventProcessor.class, processorName));
    }

    @Nullable
    private static SubscribingEventProcessorConfiguration configurationOf(SubscribingEventProcessor processor) {
        return Optional.ofNullable(processor).map(p -> {
            try {
                var field = p.getClass().getDeclaredField("configuration");
                field.setAccessible(true);
                return (SubscribingEventProcessorConfiguration) field.get(p);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).orElse(null);
    }
}