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

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * Fixture tests for load or create command handler.
 *
 * @author Marc Gathier
 */
@ExtendWith(MockitoExtension.class)
class FixtureTest_CreateOrUpdate {

    private FixtureConfiguration<Aggregate1> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Aggregate1.class);
    }

    @Test
    void testFixtureWithoutPriorActivity() {
        fixture.givenNoPriorActivity()
               .when(new CreateOrUpdateAggregate1Command("id"))
               .expectEvents(new Aggregate1CreatedOrUpdatedEvent("id"))
               .expectSuccessfulHandlerExecution();
    }

    @Test
    void testFixtureWithExistingAggregate() {
        fixture.given(new Aggregate1CreatedEvent("id"))
               .when(new CreateOrUpdateAggregate1Command("id"))
               .expectEvents(new Aggregate1CreatedOrUpdatedEvent("id"))
               .expectSuccessfulHandlerExecution();
    }


    private static class CreateAggregate1Command {

        private final String id;

        private CreateAggregate1Command(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class CreateOrUpdateAggregate1Command {

        @TargetAggregateIdentifier
        private final String id;

        private CreateOrUpdateAggregate1Command(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class Aggregate1CreatedEvent {

        private final String id;

        private Aggregate1CreatedEvent(String id) {
            this.id = id;
        }

        public String getId() {
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

        private final String id;

        private Aggregate1CreatedOrUpdatedEvent(String id) {
            this.id = id;
        }

        public String getId() {
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
        private String id;

        public Aggregate1() {
        }

        @CommandHandler
        public Aggregate1(CreateAggregate1Command command) throws Exception {
            apply(new Aggregate1CreatedEvent(command.getId()));
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
        public void handle(CreateOrUpdateAggregate1Command command) throws Exception {
            apply(new Aggregate1CreatedOrUpdatedEvent(command.getId()));
        }

        @EventSourcingHandler
        public void on(Aggregate1CreatedEvent event) throws Exception {
            this.id = event.getId();
        }

        @EventSourcingHandler
        public void on(Aggregate1CreatedOrUpdatedEvent event) throws Exception {
            this.id = event.getId();
        }
    }
}
