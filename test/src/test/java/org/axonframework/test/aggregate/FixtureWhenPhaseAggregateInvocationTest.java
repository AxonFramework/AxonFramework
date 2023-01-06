/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.test.aggregate;

import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.junit.jupiter.api.*;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Test class validating the use of {@link AggregateTestFixture#whenConstructing(Callable)} and
 * {@link AggregateTestFixture#whenInvoking(String, Consumer)}.
 *
 * @author Steven van Beelen
 */
class FixtureWhenPhaseAggregateInvocationTest {

    private static final String AGGREGATE_ID = "some-id";
    private static final String STATE = "some-state";
    private static final boolean SHOULD_NOT_FAIL = false;
    private static final boolean SHOULD_FAIL = true;

    private AggregateTestFixture<MyAggregate> testFixture;

    @BeforeEach
    void setUp() {
        testFixture = new AggregateTestFixture<>(MyAggregate.class);
    }

    @Test
    void creatingAggregateThroughWhenConstructingPublishesEventsWhenSuccessful() {
        testFixture.givenNoPriorActivity()
                   .whenConstructing(() -> new MyAggregate(AGGREGATE_ID, SHOULD_NOT_FAIL))
                   .expectEvents(new AggregateCreatedEvent(AGGREGATE_ID, SHOULD_NOT_FAIL));
    }

    @Test
    void creatingAggregateThroughWhenConstructingThrowsExceptionsWhenUnsuccessful() {
        testFixture.givenNoPriorActivity()
                   .whenConstructing(() -> new MyAggregate(AGGREGATE_ID, SHOULD_FAIL))
                   .expectNoEvents()
                   .expectException(RuntimeException.class);
    }

    @Test
    void changingAggregateThroughWhenInvokingPublishesEventsWhenSuccessful() {
        testFixture.given(new AggregateCreatedEvent(AGGREGATE_ID, SHOULD_NOT_FAIL))
                   .whenInvoking(AGGREGATE_ID, aggregate -> aggregate.createEntity(STATE, SHOULD_NOT_FAIL))
                   .expectEvents(new AggregateMemberCreatedEvent(AGGREGATE_ID, STATE, SHOULD_NOT_FAIL));
    }

    @Test
    void changingAggregateThroughWhenInvokingThrowsExceptionWhenUnsuccessful() {
        testFixture.given(new AggregateCreatedEvent(AGGREGATE_ID, SHOULD_NOT_FAIL))
                   .whenInvoking(AGGREGATE_ID, aggregate -> aggregate.createEntity(STATE, SHOULD_FAIL))
                   .expectNoEvents()
                   .expectException(RuntimeException.class);
    }

    @Test
    void changingAggregateMemberThroughWhenInvokedWorks() {
        String expectedState = "new-state";

        testFixture.given(new AggregateCreatedEvent(AGGREGATE_ID, SHOULD_NOT_FAIL),
                          new AggregateMemberCreatedEvent(AGGREGATE_ID, STATE, SHOULD_NOT_FAIL))
                   .whenInvoking(AGGREGATE_ID, aggregate -> aggregate.getMember().changeMember(expectedState))
                   .expectEvents(new AggregateMemberChangedEvent(AGGREGATE_ID, expectedState));
    }

    @Test
    void usingWhenInvokedWithNullAggregateIdentifierThrowsIllegalArgumentException() {
        testFixture.given(new AggregateCreatedEvent(AGGREGATE_ID, SHOULD_NOT_FAIL))
                   .whenInvoking(null, aggregate -> aggregate.createEntity(STATE, SHOULD_NOT_FAIL))
                   .expectException(IllegalArgumentException.class);
    }

    @Test
    void usingWhenInvokedWithNonExistingAggregateIdentifierThrowsIllegalArgumentException() {
        assertThrows(
                AssertionError.class,
                () -> testFixture.given(new AggregateCreatedEvent(AGGREGATE_ID, SHOULD_NOT_FAIL))
                                 .whenInvoking(
                                         "non-existing-aggregate-id",
                                         aggregate -> aggregate.createEntity(STATE, SHOULD_NOT_FAIL)
                                 )
        );
    }

    private static class AggregateCreatedEvent {

        private final String aggregateId;
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final boolean shouldFail;

        private AggregateCreatedEvent(String aggregateId, boolean shouldFail) {
            this.aggregateId = aggregateId;
            this.shouldFail = shouldFail;
        }
    }

    private static class AggregateMemberCreatedEvent {

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final String aggregateId;
        private final String state;
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final boolean shouldFail;

        private AggregateMemberCreatedEvent(String aggregateId, String state, boolean shouldFail) {
            this.aggregateId = aggregateId;
            this.state = state;
            this.shouldFail = shouldFail;
        }
    }

    private static class AggregateMemberChangedEvent {

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final String aggregateId;
        private final String state;

        private AggregateMemberChangedEvent(String aggregateId, String state) {
            this.aggregateId = aggregateId;
            this.state = state;
        }
    }

    private static class MyAggregate {

        @AggregateIdentifier
        private String aggregateId;
        private MyAggregateMember member;

        public MyAggregate(String aggregateId, boolean shouldFail) {
            if (shouldFail) {
                throw new RuntimeException();
            }
            //noinspection ConstantValue
            AggregateLifecycle.apply(new AggregateCreatedEvent(aggregateId, shouldFail));
        }

        public void createEntity(String memberState, boolean shouldFail) {
            if (shouldFail) {
                throw new RuntimeException();
            }
            //noinspection ConstantValue
            AggregateLifecycle.apply(new AggregateMemberCreatedEvent(aggregateId, memberState, shouldFail));
        }

        @EventSourcingHandler
        public void on(AggregateCreatedEvent event) {
            aggregateId = event.aggregateId;
        }

        @EventSourcingHandler
        public void on(AggregateMemberCreatedEvent event) {
            member = new MyAggregateMember(aggregateId, event.state);
        }

        public MyAggregateMember getMember() {
            return member;
        }

        @SuppressWarnings("unused")
        public MyAggregate() {
            // Required by Axon Framework
        }
    }

    private static class MyAggregateMember {

        private final String aggregateId;
        @SuppressWarnings("unused")
        private String state;

        private MyAggregateMember(String aggregateId, String state) {
            this.aggregateId = aggregateId;
            this.state = state;
        }

        public void changeMember(String change) {
            AggregateLifecycle.apply(new AggregateMemberChangedEvent(aggregateId, change));
        }

        @EventSourcingHandler
        public void on(AggregateMemberChangedEvent event) {
            this.state = event.state;
        }
    }
}
