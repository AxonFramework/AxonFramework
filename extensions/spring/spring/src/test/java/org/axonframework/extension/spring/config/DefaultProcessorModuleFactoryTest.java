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

package org.axonframework.extension.spring.config;

import org.axonframework.common.FutureUtils;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.Module;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating {@link DefaultProcessorModuleFactory}, and specifically how it treats an event handler that
 * brings its own {@link EventHandlingComponent} instead of a plain annotated bean.
 *
 * @author Mateusz Nowak
 */
class DefaultProcessorModuleFactoryTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final QualifiedName SOME_EVENT = new QualifiedName(SomeEvent.class);
    private static final Map<String, EventProcessorSettings> SUBSCRIBING_SETTINGS = Map.of(
            EventProcessorSettings.DEFAULT,
            (EventProcessorSettings.SubscribingEventProcessorSettings) () -> null
    );

    private @Nullable AxonConfiguration configuration;

    @AfterEach
    void tearDown() {
        if (configuration != null) {
            configuration.shutdown();
        }
    }

    @Nested
    class ADeclarativeDescriptor {

        @Test
        void isHandedToTheProcessorWithoutBeingWrappedForAnnotationInspection() {
            // given a component whose handlers annotation inspection would never find
            List<Object> handled = new CopyOnWriteArrayList<>();

            // when
            startWith(declarative("orderSaga", PlainSaga.class, "PlainSagaProcessor", handled));
            publish(new SomeEvent("order-1"));

            // then
            assertThat(handled).containsExactly(new SomeEvent("order-1"));
        }

        @Test
        void namesItsProcessorAfterTheNameItPrefers() {
            // given
            Set<EventProcessorModule> modules = modulesOf(
                    declarative("orderSaga", PlainSaga.class, "PlainSagaProcessor")
            );

            // then the preferred name is used, rather than the bean definition's package
            assertThat(names(modules)).containsExactly("EventProcessor[PlainSagaProcessor]");
        }

        @Test
        void losesItsPreferredNameToANamespaceOnTheType() {
            // given a type carrying a Namespace, the Axon Framework 5 successor of @ProcessingGroup
            Set<EventProcessorModule> modules = modulesOf(
                    declarative("orderSaga", NamespacedSaga.class, "NamespacedSagaProcessor")
            );

            // then
            assertThat(names(modules)).containsExactly("EventProcessor[orders]");
        }

        @Test
        void losesItsPreferredNameToAMatchingProcessorDefinition() {
            // given an explicitly defined processor claiming this handler
            EventProcessorDefinition definition =
                    EventProcessorDefinition.subscribing("explicit")
                                            .assigningHandlers(d -> d.beanName().equals("orderSaga"))
                                            .notCustomized();

            // when
            Set<EventProcessorModule> modules = modulesOf(
                    List.of(definition),
                    declarative("orderSaga", PlainSaga.class, "PlainSagaProcessor")
            );

            // then
            assertThat(names(modules)).containsExactly("EventProcessor[explicit]");
        }
    }

    @Nested
    class SharingOneProcessor {

        @Test
        void twoDeclarativeDescriptorsResolvingToOneNameLandOnOneProcessor() {
            // given two components explicitly grouped together, as @ProcessingGroup allowed in Axon Framework 4
            Set<EventProcessorModule> modules = modulesOf(
                    declarative("orderSaga", NamespacedSaga.class, "OrderSagaProcessor"),
                    declarative("shipmentSaga", NamespacedSaga.class, "ShipmentSagaProcessor")
            );

            // then
            assertThat(names(modules)).containsExactly("EventProcessor[orders]");
        }

        @Test
        void aDeclarativeAndAnAnnotatedDescriptorResolvingToOneNameBothHandleTheEvent() {
            // given a declarative component and a plain annotated bean sharing a processor
            List<Object> handledDeclaratively = new CopyOnWriteArrayList<>();
            NamespacedProjection projection = new NamespacedProjection();

            // when
            startWith(
                    declarative("orderSaga", NamespacedSaga.class, "OrderSagaProcessor", handledDeclaratively),
                    new StubAnnotatedDescriptor("projection", NamespacedProjection.class, projection)
            );
            publish(new SomeEvent("order-1"));

            // then both ran, which is only possible on the single processor named after the shared namespace
            assertThat(handledDeclaratively).hasSize(1);
            assertThat(projection.handled).hasSize(1);
        }
    }

    private void startWith(EventProcessorDefinition.EventHandlerDescriptor... descriptors) {
        MessagingConfigurer configurer = MessagingConfigurer.create();
        for (EventProcessorModule module : modulesOf(descriptors)) {
            configurer.componentRegistry(cr -> cr.registerModule(module));
        }
        configuration = configurer.start();
    }

    private static Set<EventProcessorModule> modulesOf(EventProcessorDefinition.EventHandlerDescriptor... descriptors) {
        return modulesOf(List.of(), descriptors);
    }

    private static Set<EventProcessorModule> modulesOf(List<EventProcessorDefinition> definitions,
                                                       EventProcessorDefinition.EventHandlerDescriptor... descriptors) {
        return new DefaultProcessorModuleFactory(definitions, SUBSCRIBING_SETTINGS)
                .buildProcessorModules(Set.of(descriptors));
    }

    private static List<String> names(Set<EventProcessorModule> modules) {
        return modules.stream().map(Module::name).toList();
    }

    private static StubDeclarativeDescriptor declarative(String beanName,
                                                         Class<?> beanType,
                                                         String preferredName) {
        return declarative(beanName, beanType, preferredName, new CopyOnWriteArrayList<>());
    }

    private static StubDeclarativeDescriptor declarative(String beanName,
                                                         Class<?> beanType,
                                                         String preferredName,
                                                         List<Object> handled) {
        return new StubDeclarativeDescriptor(beanName, beanType, preferredName, handled);
    }

    private void publish(Object payload) {
        EventMessage event = new GenericEventMessage(new MessageType(payload.getClass()), payload);
        FutureUtils.joinAndUnwrap(
                configuration.getComponent(EventSink.class).publish(null, List.of(event)), TIMEOUT
        );
    }

    /**
     * Stands in for the descriptor a Saga contributes: it carries a ready-made {@link EventHandlingComponent} whose
     * handlers no annotation inspection could uncover, which is what makes the declarative registration observable.
     */
    private record StubDeclarativeDescriptor(
            String beanName,
            Class<?> beanType,
            String preferredName,
            List<Object> handled
    ) implements EventProcessorDefinition.EventHandlerDescriptor {

        @Override
        public BeanDefinition beanDefinition() {
            return BeanDefinitionBuilder.genericBeanDefinition(beanType).getBeanDefinition();
        }

        @Override
        public Class<?> beanType() {
            return beanType;
        }

        @Override
        public Object resolveBean() {
            throw new UnsupportedOperationException("A declarative descriptor has no bean to resolve.");
        }

        @Override
        public ComponentBuilder<EventHandlingComponent> eventHandlingComponent() {
            return c -> SimpleEventHandlingComponent
                    .create(beanName)
                    .subscribe(SOME_EVENT, (event, context) -> {
                        handled.add(event.payload());
                        return MessageStream.empty();
                    });
        }

        @Override
        public Optional<String> preferredProcessorName() {
            return Optional.of(preferredName);
        }
    }

    private record StubAnnotatedDescriptor(
            String beanName,
            Class<?> beanType,
            Object bean
    ) implements EventProcessorDefinition.EventHandlerDescriptor {

        @Override
        public BeanDefinition beanDefinition() {
            return BeanDefinitionBuilder.genericBeanDefinition(beanType).getBeanDefinition();
        }

        @Override
        public Class<?> beanType() {
            return beanType;
        }

        @Override
        public Object resolveBean() {
            return bean;
        }

        @Override
        public ComponentBuilder<Object> component() {
            return c -> bean;
        }
    }

    private record SomeEvent(String id) {

    }

    private static class PlainSaga {

    }

    @Namespace("orders")
    private static class NamespacedSaga {

    }

    @Namespace("orders")
    @SuppressWarnings("unused")
    private static class NamespacedProjection {

        private final List<Object> handled = new CopyOnWriteArrayList<>();

        @EventHandler
        void on(SomeEvent event) {
            handled.add(event);
        }
    }
}
