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

package org.axonframework.extension.springboot.autoconfig;

import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.extension.spring.config.EventProcessorDefinition;
import org.axonframework.extension.spring.stereotype.Saga;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.eventstreaming.TrackingTokenSource;
import org.axonframework.modelling.saga.AssociationValue;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.modelling.saga.repository.jdbc.JdbcSagaStore;
import org.axonframework.modelling.saga.repository.jpa.JpaSagaStore;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating that an Axon Framework 4 Saga, declared with nothing but {@link Saga @Saga}, runs on Axon
 * Framework 5 Spring Boot: on a processor with the name Axon Framework 4 gave it, storing into the {@link SagaStore}
 * the auto configuration provides, and with Spring collaborators resolved as handler-method parameters.
 *
 * @author Mateusz Nowak
 */
class SagaAutoConfigurationTest {

    private static final AssociationValue ORDER_1 = new AssociationValue("orderId", "order-1");
    private static final AssociationValue PAYMENT_1 = new AssociationValue("paymentId", "payment-1");
    private static final TrackingToken FIRST = new GlobalSequenceTrackingToken(1);
    private static final TrackingToken LATEST = new GlobalSequenceTrackingToken(2);

    private ApplicationContextRunner testContext;

    @BeforeEach
    void setUp() {
        testContext = new ApplicationContextRunner()
                .withUserConfiguration(TestContext.class)
                .withPropertyValues("axon.eventstorage.jpa.polling-interval=0");
    }

    @Nested
    class RunningASaga {

        @Test
        void aStartingEventCreatesAndStoresTheSaga() {
            inMemoryStored().run(context -> {
                // given
                InMemorySagaStore sagaStore = sagaStore(context);

                // when
                publish(context, new OrderPlaced("order-1"));

                // then
                assertThat(sagaStore.findSagas(TestContext.OrderSaga.class, ORDER_1)).hasSize(1);
            });
        }

        @Test
        void aFollowUpEventReachesTheSagaThatAssociatedItself() {
            inMemoryStored().run(context -> {
                // given a Saga that associated itself with the shipment on creation
                InMemorySagaStore sagaStore = sagaStore(context);
                publish(context, new OrderPlaced("order-1"));

                // when
                publish(context, new OrderShipped("shipment-of-order-1"));

                // then
                assertThat(sagaOf(sagaStore, ORDER_1).shipped).isTrue();
            });
        }

        @Test
        void anEndingEventRemovesTheSagaFromTheStore() {
            inMemoryStored().run(context -> {
                // given
                InMemorySagaStore sagaStore = sagaStore(context);
                publish(context, new OrderPlaced("order-1"));

                // when
                publish(context, new OrderCompleted("shipment-of-order-1"));

                // then
                assertThat(sagaStore.findSagas(TestContext.OrderSaga.class, ORDER_1)).isEmpty();
            });
        }
    }

    @Nested
    class TheProcessorName {

        @Test
        void isTheOneAxonFramework4DerivedFromTheSagaType() {
            inMemoryStored().run(context -> {
                // then the name that keeps an existing token row claimable and its properties reachable
                assertThat(hasProcessor(context, "OrderSagaProcessor")).isTrue();
            });
        }

        @Test
        void isTakenOverByANamespaceOnTheSaga() {
            inMemoryStored().withUserConfiguration(NamespacedSagaContext.class).run(context -> {
                // then, the Axon Framework 5 successor of putting @ProcessingGroup on a Saga
                assertThat(hasProcessor(context, "orders")).isTrue();
                assertThat(hasProcessor(context, "NamespacedSagaProcessor")).isFalse();
            });
        }
    }

    @Nested
    class TheSagaStore {

        @Test
        void isTheJpaStoreWhenAnEntityManagerFactoryIsAvailable() {
            testContext.run(context -> {
                // then, under the bean name Axon Framework 4 gave it
                assertThat(context).hasBean("sagaStore");
                assertThat(context.getBean("sagaStore")).isInstanceOf(JpaSagaStore.class);
            });
        }

        @Test
        void isTheJdbcStoreWhenTheJpaStoreIsUnavailable() {
            testContext.withClassLoader(new FilteredClassLoader(JpaSagaStore.class)).run(context -> {
                // then
                assertThat(context.getBean("sagaStoreNoSchema")).isInstanceOf(JdbcSagaStore.class);
            });
        }

        @Test
        void fallsBackToMemoryWhenNeitherIsAvailable() {
            inMemoryStored().run(context -> {
                // then, which is what Axon Framework 4 fell back to as well
                assertThat(context.getBean("sagaStore")).isInstanceOf(InMemorySagaStore.class);
            });
        }

        @Test
        void isNotProvidedWhenTheApplicationDeclaresOne() {
            inMemoryStored().withUserConfiguration(OwnStoreContext.class).run(context -> {
                // then
                assertThat(context).hasSingleBean(SagaStore.class);
                assertThat(context.getBean(SagaStore.class)).isSameAs(context.getBean("mySagaStore"));
            });
        }

        @Test
        void isTheOneNamedOnTheAnnotationWhenTheSagaNamesOne() {
            inMemoryStored().withUserConfiguration(StoreSelectingSagaContext.class).run(context -> {
                // given two stores, only one of them named on the Saga
                InMemorySagaStore shared = sagaStore(context);
                InMemorySagaStore selected = context.getBean("customSagaStore", InMemorySagaStore.class);

                // when
                publish(context, new PaymentRequested("payment-1"));

                // then
                Class<?> sagaType = StoreSelectingSagaContext.StoreSelectingSaga.class;
                assertThat(selected.findSagas(sagaType, PAYMENT_1)).hasSize(1);
                assertThat(shared.findSagas(sagaType, PAYMENT_1)).isEmpty();
            });
        }

        @Test
        void isTheConventionallyNamedOneForASagaThatNamesNone() {
            inMemoryStored().withUserConfiguration(StoreSelectingSagaContext.class).run(context -> {
                // given a second store present only because another Saga named it
                InMemorySagaStore shared = sagaStore(context);
                InMemorySagaStore other = context.getBean("customSagaStore", InMemorySagaStore.class);

                // when
                publish(context, new OrderPlaced("order-1"));

                // then, rather than failing to pick between the two
                assertThat(shared.findSagas(TestContext.OrderSaga.class, ORDER_1)).hasSize(1);
                assertThat(other.findSagas(TestContext.OrderSaga.class, ORDER_1)).isEmpty();
            });
        }
    }

    @Nested
    class AxonFramework4Properties {

        @Test
        void configureTheSagaProcessorUnderTheNameAxonFramework4Used() {
            inMemoryStored()
                    .withPropertyValues(
                            "axon.eventhandling.processors[OrderSagaProcessor].mode=pooled",
                            "axon.eventhandling.processors[OrderSagaProcessor].initialSegmentCount=1",
                            "axon.eventhandling.processors[OrderSagaProcessor].threadCount=1",
                            "axon.eventhandling.processors[OrderSagaProcessor].batchSize=42",
                            "axon.eventhandling.processors[OrderSagaProcessor].tokenClaimInterval=7",
                            "axon.eventhandling.processors[OrderSagaProcessor].tokenClaimIntervalTimeUnit=SECONDS"
                    )
                    .run(context -> {
                        // then, with the single segment an Axon Framework 4 tracking processor defaulted to
                        PooledStreamingEventProcessorConfiguration configuration =
                                pooledConfiguration(context, "OrderSagaProcessor");
                        assertThat(configuration.initialSegmentCount()).isEqualTo(1);
                        assertThat(configuration.batchSize()).isEqualTo(42);
                        assertThat(configuration.tokenClaimInterval()).isEqualTo(7000);
                    });
        }

        @Test
        void canPutTheSagaOnASubscribingProcessor() {
            inMemoryStored()
                    .withPropertyValues("axon.eventhandling.processors[OrderSagaProcessor].mode=subscribing")
                    .run(context -> {
                        // then a subscribing processor holds no pooled configuration to find
                        assertThat(moduleConfigurationOf(context, "OrderSagaProcessor")
                                           .getOptionalComponent(PooledStreamingEventProcessorConfiguration.class))
                                .isEmpty();
                    });
        }
    }

    @Nested
    class TheInitialToken {

        @Test
        void isTheHeadOfTheStreamAsInAxonFramework4() {
            pooledSagaProcessor().run(context -> {
                // then, so that a Saga deployed onto an existing event store does not replay it and start an
                // instance per historic event
                assertThat(initialTokenOf(context, "OrderSagaProcessor")).isEqualTo(LATEST);
            });
        }

        @Test
        void isLeftToAnExplicitProcessorDefinition() {
            pooledSagaProcessor().withUserConfiguration(ReplayingDefinitionContext.class).run(context -> {
                // then the Saga default is a default only, and yields to what the application states
                assertThat(initialTokenOf(context, "OrderSagaProcessor")).isEqualTo(FIRST);
            });
        }
    }

    @Nested
    class SagaResources {

        @Test
        void areNotInjectedIntoFields() {
            inMemoryStored().run(context -> {
                // given
                InMemorySagaStore sagaStore = sagaStore(context);

                // when
                publish(context, new OrderPlaced("order-1"));

                // then
                assertThat(sagaOf(sagaStore, ORDER_1).collaborator).isNull();
            });
        }

        @Test
        void areResolvedAsHandlerParameters() {
            inMemoryStored().run(context -> {
                // given
                InMemorySagaStore sagaStore = sagaStore(context);

                // when
                publish(context, new OrderPlaced("order-1"));

                // then
                assertThat(sagaOf(sagaStore, ORDER_1).collaboratorFromParameter).isNotNull();
            });
        }
    }

    /**
     * Hides both persistent stores, so the auto configuration falls back to the in-memory store this test reads, and
     * puts every Saga on a subscribing processor, so a published event has reached it by the time publish returns.
     */
    private ApplicationContextRunner inMemoryStored() {
        return testContext
                .withClassLoader(new FilteredClassLoader(JpaSagaStore.class, JdbcSagaStore.class))
                .withPropertyValues("axon.eventhandling.processors[OrderSagaProcessor].mode=subscribing",
                                    "axon.eventhandling.processors[orders].mode=subscribing",
                                    "axon.eventhandling.processors[StoreSelectingSagaProcessor].mode=subscribing");
    }

    private ApplicationContextRunner pooledSagaProcessor() {
        return testContext
                .withClassLoader(new FilteredClassLoader(JpaSagaStore.class, JdbcSagaStore.class))
                .withPropertyValues("axon.eventhandling.processors[OrderSagaProcessor].mode=pooled");
    }

    private static void publish(ApplicationContext context, Object event) {
        context.getBean(EventGateway.class).publish(null, event);
    }

    private static InMemorySagaStore sagaStore(ApplicationContext context) {
        return context.getBean("sagaStore", InMemorySagaStore.class);
    }

    private static TestContext.OrderSaga sagaOf(InMemorySagaStore sagaStore, AssociationValue associationValue) {
        String sagaId = sagaStore.findSagas(TestContext.OrderSaga.class, associationValue).iterator().next();
        SagaStore.Entry<TestContext.OrderSaga> entry =
                sagaStore.loadSaga(TestContext.OrderSaga.class, sagaId);
        assertThat(entry).isNotNull();
        return entry.saga();
    }

    private static boolean hasProcessor(ApplicationContext context, String processorName) {
        return context.getBean(AxonConfiguration.class)
                      .getModuleConfiguration("EventProcessor[" + processorName + "]")
                      .isPresent();
    }

    private static org.axonframework.common.configuration.Configuration moduleConfigurationOf(
            ApplicationContext context,
            String processorName
    ) {
        return context.getBean(AxonConfiguration.class)
                      .getModuleConfiguration("EventProcessor[" + processorName + "]")
                      .orElseThrow();
    }

    private static PooledStreamingEventProcessorConfiguration pooledConfiguration(ApplicationContext context,
                                                                                  String processorName) {
        return moduleConfigurationOf(context, processorName)
                .getOptionalComponent(PooledStreamingEventProcessorConfiguration.class)
                .orElseThrow();
    }

    private static TrackingToken initialTokenOf(ApplicationContext context, String processorName) {
        return pooledConfiguration(context, processorName).initialToken().apply(new StubTokenSource()).join();
    }

    /**
     * Answers each end of the stream with a distinguishable token, so a test can tell which end a processor was
     * configured to start from without needing an event store holding events.
     */
    private static class StubTokenSource implements TrackingTokenSource {

        @Override
        public CompletableFuture<TrackingToken> firstToken(@Nullable ProcessingContext context) {
            return CompletableFuture.completedFuture(FIRST);
        }

        @Override
        public CompletableFuture<TrackingToken> latestToken(@Nullable ProcessingContext context) {
            return CompletableFuture.completedFuture(LATEST);
        }

        @Override
        public CompletableFuture<TrackingToken> tokenAt(Instant at, @Nullable ProcessingContext context) {
            return CompletableFuture.completedFuture(LATEST);
        }
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestContext {

        @Bean
        public TokenStore tokenStore() {
            return new InMemoryTokenStore();
        }

        @Bean
        public Collaborator collaborator() {
            return new Collaborator();
        }

        @Saga
        @SuppressWarnings("unused")
        static class OrderSaga {

            @Autowired
            transient @Nullable Collaborator collaborator;

            transient @Nullable Collaborator collaboratorFromParameter;

            boolean shipped;

            @StartSaga
            @SagaEventHandler(associationProperty = "orderId")
            void on(OrderPlaced event, SagaLifecycle lifecycle, Collaborator collaborator) {
                this.collaboratorFromParameter = collaborator;
                lifecycle.associateWith("shipmentId", "shipment-of-" + event.orderId());
            }

            @SagaEventHandler(associationProperty = "shipmentId")
            void on(OrderShipped event) {
                this.shipped = true;
            }

            @EndSaga
            @SagaEventHandler(associationProperty = "shipmentId")
            void on(OrderCompleted event) {
                // Ends the Saga.
            }
        }
    }

    @Configuration
    static class NamespacedSagaContext {

        @Saga
        @Namespace("orders")
        @SuppressWarnings("unused")
        static class NamespacedSaga {

            @StartSaga
            @SagaEventHandler(associationProperty = "orderId")
            void on(OrderPlaced event) {
                // Only its processor name matters here.
            }
        }
    }

    @Configuration
    static class OwnStoreContext {

        @Bean
        public SagaStore<Object> mySagaStore() {
            return new InMemorySagaStore();
        }
    }

    @Configuration
    static class StoreSelectingSagaContext {

        /**
         * Declared next to {@link #customSagaStore()} because naming a store on one Saga suppresses the auto
         * configured fallback for every Saga. Under the conventional name, so the Sagas that name no store keep
         * resolving one, which is what Axon Framework 4 did.
         */
        @Bean
        public InMemorySagaStore sagaStore() {
            return new InMemorySagaStore();
        }

        @Bean
        public InMemorySagaStore customSagaStore() {
            return new InMemorySagaStore();
        }

        @Saga(sagaStore = "customSagaStore")
        @SuppressWarnings("unused")
        static class StoreSelectingSaga {

            @StartSaga
            @SagaEventHandler(associationProperty = "paymentId")
            void on(PaymentRequested event) {
                // Only where it is stored matters here.
            }
        }
    }

    @Configuration
    static class ReplayingDefinitionContext {

        @Bean
        public EventProcessorDefinition orderSagaProcessorDefinition() {
            return EventProcessorDefinition
                    .pooledStreaming("OrderSagaProcessor")
                    .assigningHandlers(descriptor -> descriptor.beanType() == TestContext.OrderSaga.class)
                    .customized(configuration -> configuration.initialToken(source -> source.firstToken(null)));
        }
    }

    static class Collaborator {

    }

    record OrderPlaced(String orderId) {

    }

    record OrderShipped(String shipmentId) {

    }

    record OrderCompleted(String shipmentId) {

    }

    record PaymentRequested(String paymentId) {

    }
}
