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

package org.axonframework.test.saga;

import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.modelling.saga.configuration.Sagas;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.test.fixture.AxonTestFixture;
import org.axonframework.test.fixture.AxonTestPhase;
import org.axonframework.test.fixture.AxonTestPhase.Given;
import org.axonframework.test.fixture.AxonTestPhase.When;
import org.axonframework.test.matchers.MatchAllFieldFilter;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fixture for testing Sagas in a given-when-then style, carrying the Axon Framework 4 API so a migrating test suite
 * keeps its shape.
 * <p>
 * A layer over {@link AxonTestFixture}: the Saga is registered on a subscribing event processor, which handles on the
 * publishing thread, so a "when" call has already completed by the time the assertions run and no waiting is needed.
 * The Sagas are kept in an {@link InMemorySagaStore}, which the store assertions read.
 * <p>
 * Example:
 * <pre>{@code
 * SagaTestFixture<OrderSaga> fixture = new SagaTestFixture<>(OrderSaga.class);
 * fixture.givenAPublished(new OrderPlaced("order-1"))
 *        .whenPublishingA(new OrderShipped("shipment-of-order-1"))
 *        .expectActiveSagas(1)
 *        .expectAssociationWith("orderId", "order-1");
 * }</pre>
 * A test that does not need the Axon Framework 4 API can drive the same Saga through {@code AxonTestFixture} directly
 * and use {@link SagaAssertions} for the store assertions.
 *
 * @param <T> the type of Saga under test
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @since 5.4.0
 */
public class SagaTestFixture<T> implements FixtureConfiguration, ContinuedGivenState {

    private final Class<T> sagaType;
    private final InMemorySagaStore sagaStore = new InMemorySagaStore();

    @Nullable
    private AxonTestFixture fixture;
    @Nullable
    private Given givenPhase;
    @Nullable
    private When whenPhase;

    /**
     * Creates an instance for testing Sagas of the given {@code sagaType}.
     *
     * @param sagaType the type of Saga under test
     */
    public SagaTestFixture(Class<T> sagaType) {
        this.sagaType = Objects.requireNonNull(sagaType, "The sagaType may not be null.");
    }

    @Override
    public GivenAggregateEventPublisher givenAggregate(String aggregateIdentifier) {
        given();
        return new AggregateEventPublisher();
    }

    @Override
    public ContinuedGivenState givenAPublished(Object event) {
        givenPhase = given().event(event);
        return this;
    }

    @Override
    public ContinuedGivenState givenAPublished(Object event, Map<String, String> metadata) {
        givenPhase = given().event(event, metadata);
        return this;
    }

    @Override
    public WhenState givenNoPriorActivity() {
        given().noPriorActivity();
        return this;
    }

    @Override
    public GivenAggregateEventPublisher andThenAggregate(String aggregateIdentifier) {
        return givenAggregate(aggregateIdentifier);
    }

    @Override
    public ContinuedGivenState andThenAPublished(Object event) {
        return givenAPublished(event);
    }

    @Override
    public ContinuedGivenState andThenAPublished(Object event, Map<String, String> metadata) {
        return givenAPublished(event, metadata);
    }

    @Override
    public WhenAggregateEventPublisher whenAggregate(String aggregateIdentifier) {
        // Axon Framework 4 started recording here, before handing out the publisher. Entering the when-phase resets
        // the recorders, which is the same moment.
        whenPhase = given().when();
        return new AggregateEventPublisher();
    }

    @Override
    public FixtureExecutionResult whenPublishingA(Object event) {
        return resultOf(when().event(event));
    }

    @Override
    public FixtureExecutionResult whenPublishingA(Object event, Map<String, String> metadata) {
        return resultOf(when().event(event, metadata));
    }

    /**
     * Shuts the configuration this fixture started down.
     * <p>
     * New in Axon Framework 5: the fixture runs a started {@code AxonConfiguration}, which holds an event processor
     * that has to be stopped.
     */
    public void stop() {
        if (fixture != null) {
            fixture.stop();
        }
    }

    /**
     * The given-phase of the delegate fixture, starting the configuration on first use.
     * <p>
     * Starting lazily reproduces the Axon Framework 4 behaviour that the fixture is wired once, on the first given or
     * when call, and that anything configured after that point is ignored.
     */
    private Given given() {
        if (givenPhase == null) {
            fixture = AxonTestFixture.with(configurer(), c -> c.excludeWhenPhaseMessages());
            givenPhase = fixture.given();
        }
        return givenPhase;
    }

    private When when() {
        if (whenPhase == null) {
            whenPhase = given().when();
        }
        return whenPhase;
    }

    private MessagingConfigurer configurer() {
        return MessagingConfigurer
                .create()
                .componentRegistry(cr -> cr.registerComponent(SagaStore.class, c -> sagaStore))
                .eventProcessing(processing -> processing.subscribing(
                        subscribing -> subscribing.defaultProcessor(
                                sagaType.getSimpleName(),
                                components -> components.declarative("Saga[" + sagaType.getSimpleName() + "]",
                                                                     Sagas.of(sagaType)))
                ));
    }

    private FixtureExecutionResult resultOf(AxonTestPhase.When.Event event) {
        return new FixtureExecutionResultImpl(sagaType, event.then(), new MatchAllFieldFilter(List.of()));
    }

    /**
     * Publishes events on behalf of an aggregate.
     * <p>
     * Axon Framework 4 wrapped these in a {@code DomainEventMessage} carrying the aggregate identifier, a synthesized
     * aggregate type and a sequence number. Axon Framework 5 has no such message, so the aggregate identifier only
     * shapes the test's narrative here, and nothing of it reaches the Saga.
     */
    private class AggregateEventPublisher implements GivenAggregateEventPublisher, WhenAggregateEventPublisher {

        @Override
        public ContinuedGivenState published(Object... events) {
            for (Object event : events) {
                givenAPublished(event);
            }
            return SagaTestFixture.this;
        }

        @Override
        public FixtureExecutionResult publishes(Object event) {
            return resultOf(when().event(event));
        }

        @Override
        public FixtureExecutionResult publishes(Object event, Map<String, String> metadata) {
            return resultOf(when().event(event, metadata));
        }
    }
}
