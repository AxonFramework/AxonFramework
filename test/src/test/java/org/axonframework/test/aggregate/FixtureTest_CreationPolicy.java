/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CommandHandlerInterceptor;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.junit.jupiter.api.*;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Fixture tests for validating {@link CreationPolicy} annotated command handlers.
 *
 * @author Marc Gathier
 * @author Steven van Beelen
 */
class FixtureTest_CreationPolicy {

    private static final ComplexAggregateId AGGREGATE_ID = new ComplexAggregateId(UUID.randomUUID(), 42);
    private static final boolean PUBLISH_EVENTS = true;
    private static final boolean PUBLISH_NO_EVENTS = false;

    private FixtureConfiguration<TestAggregate> fixture;

    private static AtomicBoolean intercepted;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(TestAggregate.class);

        intercepted = new AtomicBoolean(false);
    }

    @Test
    void createOrUpdatePolicyForNewInstance() {
        fixture.givenNoPriorActivity()
               .when(new CreateOrUpdateCommand(AGGREGATE_ID, PUBLISH_EVENTS))
               .expectEvents(new CreatedOrUpdatedEvent(AGGREGATE_ID))
               .expectSuccessfulHandlerExecution();
        assertTrue(intercepted.get());
    }

    @Test
    void createOrUpdatePolicyForExistingInstance() {
        fixture.given(new CreatedEvent(AGGREGATE_ID))
               .when(new CreateOrUpdateCommand(AGGREGATE_ID, PUBLISH_EVENTS))
               .expectEvents(new CreatedOrUpdatedEvent(AGGREGATE_ID))
               .expectSuccessfulHandlerExecution();
        assertTrue(intercepted.get());
    }

    @Test
    void alwaysCreatePolicyWithoutResultReturnsAggregateId() {
        fixture.givenNoPriorActivity()
               .when(new AlwaysCreateWithoutResultCommand(AGGREGATE_ID, PUBLISH_EVENTS))
               .expectEvents(new AlwaysCreatedEvent(AGGREGATE_ID))
               .expectResultMessagePayload(AGGREGATE_ID)
               .expectSuccessfulHandlerExecution();
        assertTrue(intercepted.get());
    }

    @Test
    void alwaysCreatePolicyWithResultReturnsCommandHandlingResult() {
        Object testResult = "some-result";
        fixture.givenNoPriorActivity()
               .when(new AlwaysCreateWithResultCommand(AGGREGATE_ID, testResult))
               .expectEvents(new AlwaysCreatedEvent(AGGREGATE_ID))
               .expectResultMessagePayload(testResult)
               .expectSuccessfulHandlerExecution();
        assertTrue(intercepted.get());
    }

    @Test
    void alwaysCreatePolicyWithResultReturnsNullCommandHandlingResult() {
        fixture.givenNoPriorActivity()
               .when(new AlwaysCreateWithResultCommand(AGGREGATE_ID, null))
               .expectEvents(new AlwaysCreatedEvent(AGGREGATE_ID))
               .expectResultMessagePayload(null)
               .expectSuccessfulHandlerExecution();
        assertTrue(intercepted.get());
    }

    @Test
    void neverCreatePolicy() {
        fixture.given(new CreatedEvent(AGGREGATE_ID))
               .when(new ExecuteOnExistingCommand(AGGREGATE_ID))
               .expectEvents(new ExecutedOnExistingEvent(AGGREGATE_ID))
               .expectSuccessfulHandlerExecution();
        assertTrue(intercepted.get());
    }

    @Test
    void alwaysCreatePolicyWithStateReturnsStateInCommandHandlingResult() {
        fixture.givenNoPriorActivity()
               .when(new AlwaysCreateWithEventSourcedResultCommand(AGGREGATE_ID))
               .expectEvents(new AlwaysCreatedEvent(AGGREGATE_ID))
               .expectResultMessagePayload(AGGREGATE_ID)
               .expectSuccessfulHandlerExecution();
        assertTrue(intercepted.get());
    }

    @Test
    void alwaysCreateExceptionsArePropagates() {
        fixture.givenNoPriorActivity()
               .when(new AlwaysCreateWithEventSourcedResultCommand(AGGREGATE_ID, PUBLISH_NO_EVENTS))
               .expectNoEvents()
               .expectException(RuntimeException.class);
        assertTrue(intercepted.get());
    }

    @Test
    void createOrUpdatePolicyDoesNotPublishAnyEvents() {
        fixture.givenNoPriorActivity()
               .when(new CreateOrUpdateCommand(AGGREGATE_ID, PUBLISH_NO_EVENTS))
               .expectNoEvents()
               .expectSuccessfulHandlerExecution();
    }

    private static class CreateCommand {

        @TargetAggregateIdentifier
        private final ComplexAggregateId id;

        private CreateCommand(ComplexAggregateId id) {
            this.id = id;
        }

        public ComplexAggregateId getId() {
            return id;
        }
    }

    private static class CreateOrUpdateCommand {

        @TargetAggregateIdentifier
        private final ComplexAggregateId id;
        private final boolean shouldPublishEvent;

        private CreateOrUpdateCommand(ComplexAggregateId id, boolean shouldPublishEvent) {
            this.id = id;
            this.shouldPublishEvent = shouldPublishEvent;
        }

        public ComplexAggregateId getId() {
            return id;
        }

        public boolean shouldPublishEvent() {
            return shouldPublishEvent;
        }
    }

    private static class AlwaysCreateWithoutResultCommand {

        @TargetAggregateIdentifier
        private final ComplexAggregateId id;
        private final boolean shouldPublishEvent;

        private AlwaysCreateWithoutResultCommand(ComplexAggregateId id, boolean shouldPublishEvent) {
            this.id = id;
            this.shouldPublishEvent = shouldPublishEvent;
        }

        public ComplexAggregateId getId() {
            return id;
        }

        public boolean shouldPublishEvent() {
            return shouldPublishEvent;
        }
    }

    private static class AlwaysCreateWithResultCommand {

        @TargetAggregateIdentifier
        private final ComplexAggregateId id;
        private final Object result;

        private AlwaysCreateWithResultCommand(ComplexAggregateId id, Object result) {
            this.id = id;
            this.result = result;
        }

        public ComplexAggregateId getId() {
            return id;
        }

        public Object getResult() {
            return result;
        }
    }

    private static class AlwaysCreateWithEventSourcedResultCommand {

        @TargetAggregateIdentifier
        private final ComplexAggregateId id;
        private final boolean success;

        private AlwaysCreateWithEventSourcedResultCommand(ComplexAggregateId id) {
            this(id, true);
        }

        private AlwaysCreateWithEventSourcedResultCommand(ComplexAggregateId id, boolean success) {
            this.id = id;
            this.success = success;
        }

        public ComplexAggregateId getId() {
            return id;
        }
    }

    private static class ExecuteOnExistingCommand {

        @TargetAggregateIdentifier
        private final ComplexAggregateId id;

        private ExecuteOnExistingCommand(ComplexAggregateId id) {
            this.id = id;
        }

        public ComplexAggregateId getId() {
            return id;
        }
    }

    private static class CreatedEvent {

        private final ComplexAggregateId id;

        private CreatedEvent(ComplexAggregateId id) {
            this.id = id;
        }

        public ComplexAggregateId getId() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CreatedEvent that = (CreatedEvent) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class CreatedOrUpdatedEvent {

        private final ComplexAggregateId id;

        private CreatedOrUpdatedEvent(ComplexAggregateId id) {
            this.id = id;
        }

        public ComplexAggregateId getId() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CreatedOrUpdatedEvent that = (CreatedOrUpdatedEvent) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class AlwaysCreatedEvent {

        private final ComplexAggregateId id;

        private AlwaysCreatedEvent(ComplexAggregateId id) {
            this.id = id;
        }

        public ComplexAggregateId getId() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AlwaysCreatedEvent that = (AlwaysCreatedEvent) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class ExecutedOnExistingEvent {

        private final ComplexAggregateId id;

        private ExecutedOnExistingEvent(ComplexAggregateId id) {
            this.id = id;
        }

        public ComplexAggregateId getId() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ExecutedOnExistingEvent that = (ExecutedOnExistingEvent) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    @SuppressWarnings("unused")
    public static class TestAggregate {

        @AggregateIdentifier
        private ComplexAggregateId id;

        public TestAggregate() {
        }

        @CommandHandlerInterceptor
        public void intercept(Object command) {
            intercepted.set(true);
        }

        @CommandHandler
        public TestAggregate(CreateCommand command) {
            apply(new CreatedEvent(command.getId()));
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
        public void handle(CreateOrUpdateCommand command) {
            if (command.shouldPublishEvent()) {
                apply(new CreatedOrUpdatedEvent(command.getId()));
            }
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(AlwaysCreateWithoutResultCommand command) {
            if (command.shouldPublishEvent()) {
                apply(new AlwaysCreatedEvent(command.getId()));
            }
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public Object handle(AlwaysCreateWithResultCommand command) {
            apply(new AlwaysCreatedEvent(command.getId()));
            return command.getResult();
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public ComplexAggregateId handle(AlwaysCreateWithEventSourcedResultCommand command) {
            apply(new AlwaysCreatedEvent(command.getId()));
            if (!command.success) {
                throw new RuntimeException("Simulating failure in a creation handler");
            }
            // On apply, the event sourcing handlers should be invoked first.
            // Hence, we should be able to return the identifier of the aggregate directly.
            return id;
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.NEVER)
        public void handle(ExecuteOnExistingCommand command) {
            apply(new ExecutedOnExistingEvent(command.getId()));
        }

        @EventSourcingHandler
        public void on(CreatedEvent event) {
            this.id = event.getId();
        }

        @EventSourcingHandler
        public void on(CreatedOrUpdatedEvent event) {
            this.id = event.getId();
        }

        @EventSourcingHandler
        public void on(AlwaysCreatedEvent event) {
            this.id = event.getId();
        }
    }

    /**
     * Test id introduces due too https://github.com/AxonFramework/AxonFramework/pull/1356
     */
    private static class ComplexAggregateId {

        private final UUID actualId;
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final Integer someOtherField;

        private ComplexAggregateId(UUID actualId, Integer someOtherField) {
            this.actualId = actualId;
            this.someOtherField = someOtherField;
        }

        @Override
        public String toString() {
            return actualId.toString();
        }
    }
}
