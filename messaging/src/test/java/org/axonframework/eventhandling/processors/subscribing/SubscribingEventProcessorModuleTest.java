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

package org.axonframework.eventhandling.processors.subscribing;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.transaction.NoOpTransactionManager;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.RecordingEventHandlingComponent;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.annotations.EventHandler;
import org.axonframework.eventhandling.configuration.EventHandlingComponentBuilder;
import org.axonframework.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.processors.errorhandling.ErrorHandler;
import org.axonframework.eventhandling.processors.errorhandling.PropagatingErrorHandler;
import org.axonframework.messaging.EmptyApplicationContext;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.TransactionalUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWorkTestUtils;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
            var component3HandledPayload = new AtomicReference<String>();
            var component3 = new Object() {
                @EventHandler
                public void handle(String event) {
                    component3HandledPayload.set(event);
                }
            };
            configurer.eventProcessing(
                    ep -> ep.subscribing(
                            sp -> sp.defaults(d -> d.messageSource(eventBus))
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
            EventMessage sampleEvent = EventTestUtils.asEventMessage("test-event");
            eventBus.publish(sampleEvent);

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
    class InterceptorTest {

        @Test
        void registeredInterceptorsShouldBeInvoked() {
            // given...
            MessagingConfigurer configurer = MessagingConfigurer.create();
            SimpleEventBus eventBus = SimpleEventBus.builder().build();
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
            SubscribingEventProcessorModule sepModuleOne =
                    EventProcessorModule.subscribing("processor-one")
                                        .eventHandlingComponents(
                                                components -> components.declarative(cfg -> componentOne)
                                        )
                                        .customized((config, psepConfig) -> psepConfig.withInterceptor(
                                                specificInterceptorOne
                                        ));
            RecordingEventHandlingComponent componentTwo = simpleRecordingTestComponent(new QualifiedName(Integer.class));
            SubscribingEventProcessorModule sepModuleTwo =
                    EventProcessorModule.subscribing("processor-two")
                                        .eventHandlingComponents(
                                                components -> components.declarative(cfg -> componentTwo)
                                        )
                                        .customized((config, psepConfig) -> psepConfig.withInterceptor(
                                                specificInterceptorTwo
                                        ));
            // Register the global interceptor
            configurer.registerEventHandlerInterceptor(c -> globalInterceptor);
            // Register the default interceptor and attach both PSEP modules.
            configurer.eventProcessing(processingConfigurer -> processingConfigurer.subscribing(
                    sepConfigurer -> sepConfigurer.defaults(defaults -> defaults.messageSource(eventBus)
                                                                                .withInterceptor(defaultInterceptor))
                                                  .processor(sepModuleOne)
                                                  .processor(sepModuleTwo)
            ));

            AxonConfiguration configuration = configurer.build();
            configuration.start();

            // When publishing a String event
            EventMessage stringEvent = EventTestUtils.asEventMessage("test-event");
            eventBus.publish(stringEvent);

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
            eventBus.publish(integerEvent);

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
        void moduleWithGlobalMessageSource() {
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
        void defaultsDoNotOverrideGlobalComponents() {
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
        void defaultsDoNotRegisterGlobalComponents() {
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

    @Nonnull
    private static TransactionalUnitOfWorkFactory aTransactionalUnitOfWork() {
        return UnitOfWorkTestUtils.transactionalUnitOfWorkFactory(new NoOpTransactionManager());
    }

    private static RecordingEventHandlingComponent simpleRecordingTestComponent() {
        return simpleRecordingTestComponent(new QualifiedName(String.class));
    }

    private static RecordingEventHandlingComponent simpleRecordingTestComponent(
            @Nonnull QualifiedName supportedEventName
    ) {
        return new RecordingEventHandlingComponent(
                EventHandlingComponentBuilder.builder()
                                             .handles(supportedEventName, (e, c) -> MessageStream.empty())
                                             .build()
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