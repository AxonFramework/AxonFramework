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

import org.axonframework.extension.spring.stereotype.Saga;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link SpringSagaLookup}.
 *
 * @author Mateusz Nowak
 */
class SpringSagaLookupTest {

    @Nested
    class Discovery {

        @Test
        void registersADescriptorPerAnnotatedSaga() {
            // given
            DefaultListableBeanFactory beanFactory = beanFactoryWith(OrderSaga.class, ShipmentSaga.class);

            // when
            new SpringSagaLookup().postProcessBeanFactory(beanFactory);

            // then
            assertThat(beanFactory.getBeanNamesForType(SpringSagaDescriptor.class))
                    .containsExactlyInAnyOrder("orderSaga$$Registrar", "shipmentSaga$$Registrar");
        }

        @Test
        void namesTheProcessorAfterTheSagaAsAxonFramework4Did() {
            // given
            DefaultListableBeanFactory beanFactory = beanFactoryWith(OrderSaga.class);

            // when
            new SpringSagaLookup().postProcessBeanFactory(beanFactory);

            // then the name Axon Framework 4 gave it, which keeps its token and its properties reachable
            SpringSagaDescriptor descriptor = beanFactory.getBean("orderSaga$$Registrar", SpringSagaDescriptor.class);
            assertThat(descriptor.preferredProcessorName()).contains("OrderSagaProcessor");
            assertThat(descriptor.beanType()).isEqualTo(OrderSaga.class);
            assertThat(descriptor.beanName()).isEqualTo("orderSaga");
        }

        @Test
        void carriesTheStoreNamedOnTheAnnotation() {
            // given
            DefaultListableBeanFactory beanFactory = beanFactoryWith(StoreSelectingSaga.class);

            // when
            new SpringSagaLookup().postProcessBeanFactory(beanFactory);

            // then
            BeanDefinition definition = beanFactory.getBeanDefinition("storeSelectingSaga$$Registrar");
            assertThat(definition.getPropertyValues().get("sagaStore")).isEqualTo("customSagaStore");
        }

        @Test
        void leavesTheStoreUnsetWhenTheAnnotationNamesNone() {
            // given
            DefaultListableBeanFactory beanFactory = beanFactoryWith(OrderSaga.class);

            // when
            new SpringSagaLookup().postProcessBeanFactory(beanFactory);

            // then, so that the descriptor falls back to the SagaStore of the configuration
            BeanDefinition definition = beanFactory.getBeanDefinition("orderSaga$$Registrar");
            assertThat(definition.getPropertyValues().contains("sagaStore")).isFalse();
        }
    }

    @Nested
    class NotAlsoAPlainEventHandler {

        @Test
        void isNotFoundByTheMessageHandlerLookup() {
            // given a Saga carrying @SagaEventHandler methods, which are meta-annotated event handlers
            DefaultListableBeanFactory beanFactory = beanFactoryWith(OrderSaga.class);

            // when
            var found = MessageHandlerLookup.messageHandlerBeans(EventMessage.class, beanFactory);

            // then the prototype scope of @Saga keeps it out, so it is not registered twice
            assertThat(found).isEmpty();
        }

        @Test
        void isFoundOnlyWhenPrototypesAreExplicitlyIncluded() {
            // given
            DefaultListableBeanFactory beanFactory = beanFactoryWith(OrderSaga.class);

            // when
            var found = MessageHandlerLookup.messageHandlerBeans(EventMessage.class, beanFactory, true);

            // then, which is what shows the exclusion above is the scope and not a missing handler
            assertThat(found).containsExactly("orderSaga");
        }
    }

    @Nested
    class AnExistingRegistrar {

        @Test
        void isNotReplaced() {
            // given a registrar already contributed by hand
            DefaultListableBeanFactory beanFactory = beanFactoryWith(OrderSaga.class);
            BeanDefinition existing = BeanDefinitionBuilder.genericBeanDefinition(String.class).getBeanDefinition();
            beanFactory.registerBeanDefinition("orderSaga$$Registrar", existing);

            // when
            new SpringSagaLookup().postProcessBeanFactory(beanFactory);

            // then
            assertThat(beanFactory.getBeanDefinition("orderSaga$$Registrar")).isSameAs(existing);
        }

        @Test
        void stopsEveryLaterSagaFromBeingRegistered() {
            // given a registrar already present for the Saga that happens to be visited first
            DefaultListableBeanFactory beanFactory = beanFactoryWith(OrderSaga.class, ShipmentSaga.class);
            beanFactory.registerBeanDefinition(
                    firstVisited(beanFactory) + "$$Registrar",
                    BeanDefinitionBuilder.genericBeanDefinition(String.class).getBeanDefinition()
            );

            // when
            new SpringSagaLookup().postProcessBeanFactory(beanFactory);

            // then no descriptor at all, because Axon Framework 4 broke out of the loop where it meant to continue.
            // Carried over rather than corrected: the registrar is only ever contributed by this lookup, so the
            // branch is unreachable in practice, and this test is here to state that it was not an oversight.
            assertThat(beanFactory.getBeanNamesForType(SpringSagaDescriptor.class)).isEmpty();
        }

        private String firstVisited(DefaultListableBeanFactory beanFactory) {
            return beanFactory.getBeanNamesForAnnotation(Saga.class)[0];
        }
    }

    /**
     * Registers each Saga type under the bean name a component scan of a top-level class would give it, while still
     * letting the {@link Saga @Saga} annotation drive the bean definition, so that the prototype scope under test is
     * the one the annotation declares rather than one this method set.
     */
    private static DefaultListableBeanFactory beanFactoryWith(Class<?>... sagaTypes) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        AnnotatedBeanDefinitionReader reader = new AnnotatedBeanDefinitionReader(beanFactory);
        for (Class<?> sagaType : sagaTypes) {
            reader.registerBean(sagaType, decapitalized(sagaType.getSimpleName()));
        }
        return beanFactory;
    }

    private static String decapitalized(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    @Saga
    @SuppressWarnings("unused")
    static class OrderSaga {

        @StartSaga
        @SagaEventHandler(associationProperty = "orderId")
        void on(OrderPlaced event) {
            // Only its presence matters here.
        }
    }

    @Saga
    static class ShipmentSaga {

    }

    @Saga(sagaStore = "customSagaStore")
    static class StoreSelectingSaga {

    }

    record OrderPlaced(String orderId) {

    }
}
