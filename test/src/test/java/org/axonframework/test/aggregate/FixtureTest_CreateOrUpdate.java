/*
 * Copyright (c) 2010-2020. Axon Framework
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
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.*;

import java.util.Objects;
import java.util.UUID;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * Fixture tests for load or create command handler.
 *
 * @author Marc Gathier
 */
@ExtendWith(MockitoExtension.class)
class FixtureTest_CreateOrUpdate {

    private static final ComplexAggregateId AGGREGATE_ID = new ComplexAggregateId(UUID.randomUUID(), 42);

    private FixtureConfiguration<Aggregate1> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Aggregate1.class);
    }

    @Test
    void testFixtureWithoutPriorActivity() {
        fixture.givenNoPriorActivity()
               .when(new CreateOrUpdateAggregate1Command(AGGREGATE_ID))
               .expectEvents(new Aggregate1CreatedOrUpdatedEvent(AGGREGATE_ID))
               .expectSuccessfulHandlerExecution();
    }

    @Test
    void testFixtureWithExistingAggregate() {
        fixture.given(new Aggregate1CreatedEvent(AGGREGATE_ID))
               .when(new CreateOrUpdateAggregate1Command(AGGREGATE_ID))
               .expectEvents(new Aggregate1CreatedOrUpdatedEvent(AGGREGATE_ID))
               .expectSuccessfulHandlerExecution();
    }

    private static class CreateAggregate1Command {

        @TargetAggregateIdentifier
        private final ComplexAggregateId id;

        private CreateAggregate1Command(ComplexAggregateId id) {
            this.id = id;
        }

        public ComplexAggregateId getId() {
            return id;
        }
    }

    private static class CreateOrUpdateAggregate1Command {

        @TargetAggregateIdentifier
        private final ComplexAggregateId id;

        private CreateOrUpdateAggregate1Command(ComplexAggregateId id) {
            this.id = id;
        }

        public ComplexAggregateId getId() {
            return id;
        }
    }

    private static class Aggregate1CreatedEvent {

        private final ComplexAggregateId id;

        private Aggregate1CreatedEvent(ComplexAggregateId id) {
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
            Aggregate1CreatedEvent that = (Aggregate1CreatedEvent) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class Aggregate1CreatedOrUpdatedEvent {

        private final ComplexAggregateId id;

        private Aggregate1CreatedOrUpdatedEvent(ComplexAggregateId id) {
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
            Aggregate1CreatedOrUpdatedEvent that = (Aggregate1CreatedOrUpdatedEvent) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }


    @SuppressWarnings("unused")
    public static class Aggregate1 {

        @AggregateIdentifier
        private ComplexAggregateId id;

        public Aggregate1() {
        }

        @CommandHandler
        public Aggregate1(CreateAggregate1Command command) {
            apply(new Aggregate1CreatedEvent(command.getId()));
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
        public void handle(CreateOrUpdateAggregate1Command command) {
            apply(new Aggregate1CreatedOrUpdatedEvent(command.getId()));
        }

        @EventSourcingHandler
        public void on(Aggregate1CreatedEvent event) {
            this.id = event.getId();
        }

        @EventSourcingHandler
        public void on(Aggregate1CreatedOrUpdatedEvent event) {
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
