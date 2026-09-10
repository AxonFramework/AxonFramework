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

import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.common.configuration.DuplicateModuleRegistrationException;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class validating {@link SagaProcessorConfigurer}: the dedicated, Saga-only event processor wiring, kept
 * entirely separate from {@link DefaultProcessorModuleFactory}'s shared handler-assignment pipeline.
 *
 * @author Mateusz Nowak
 */
class SagaProcessorConfigurerTest {

    private static final EventProcessorSettings.SubscribingEventProcessorSettings SUBSCRIBING_SETTINGS = () -> null;

    private @Nullable AxonConfiguration configuration;
    private @Nullable GenericApplicationContext applicationContext;

    @AfterEach
    void tearDown() {
        if (configuration != null) {
            configuration.shutdown();
        }
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    @Nested
    class NoSagas {

        @Test
        void doesNotTouchSettingsAtAll() {
            // given an application context that would fail if the configurer looked up settings unconditionally
            GenericApplicationContext context = new GenericApplicationContext();
            context.refresh();

            // when / then
            assertThatCode(() -> enhance(context)).doesNotThrowAnyException();
        }
    }

    @Nested
    class ProcessorNaming {

        @Test
        void namesTheProcessorAfterTheSagaType() {
            // given
            enhance(context(saga("orderSaga", PlainSaga.class)));

            // then
            assertThat(hasProcessor("PlainSagaProcessor")).isTrue();
        }

        @Test
        void losesItsDefaultNameToANamespaceOnTheType() {
            // given a Saga type carrying a Namespace, the Axon Framework 5 successor of @ProcessingGroup
            enhance(context(saga("orderSaga", NamespacedSaga.class)));

            // then
            assertThat(hasProcessor("orders")).isTrue();
            assertThat(hasProcessor("NamespacedSagaProcessor")).isFalse();
        }
    }

    @Nested
    class TwoSagasClaimingTheSameProcessor {

        @Test
        void isRejectedRatherThanSilentlyMerged() {
            // given two Sagas whose Namespace resolves to the same processor name
            GenericApplicationContext context = context(
                    saga("orderSaga", NamespacedSaga.class),
                    saga("shipmentSaga", AlsoNamespacedSaga.class)
            );

            // then, unlike Axon Framework 4, a Saga is never grouped with anything else
            assertThatThrownBy(() -> enhance(context))
                    .isInstanceOf(DuplicateModuleRegistrationException.class);
        }
    }

    private void enhance(GenericApplicationContext context) {
        applicationContext = context;
        SagaProcessorConfigurer configurer = new SagaProcessorConfigurer();
        configurer.setApplicationContext(context);
        MessagingConfigurer messaging = MessagingConfigurer.create();
        // The SagaStore Sagas are kept in is an Axon component, resolved from the Configuration, not a Spring bean.
        messaging.componentRegistry(cr -> cr.registerComponent(SagaStore.class, c -> new InMemorySagaStore()));
        messaging.componentRegistry(configurer::enhance);
        configuration = messaging.start();
    }

    private boolean hasProcessor(String processorName) {
        return configuration.getModuleConfiguration("EventProcessor[" + processorName + "]").isPresent();
    }

    private static GenericApplicationContext context(SpringSagaDescriptor... sagas) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(
                EventProcessorSettings.MapWrapper.class,
                () -> new EventProcessorSettings.MapWrapper(Map.of(EventProcessorSettings.DEFAULT, SUBSCRIBING_SETTINGS))
        );
        for (SpringSagaDescriptor saga : sagas) {
            context.registerBean(saga.beanName(), SpringSagaDescriptor.class, () -> saga);
        }
        context.refresh();
        return context;
    }

    private static SpringSagaDescriptor saga(String beanName, Class<?> sagaType) {
        return new SpringSagaDescriptor(beanName, sagaType);
    }

    private static class PlainSaga {

    }

    @Namespace("orders")
    private static class NamespacedSaga {

    }

    @Namespace("orders")
    private static class AlsoNamespacedSaga {

    }
}
