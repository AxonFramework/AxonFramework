/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.axonframework.eventsourcing.EventSourcedAggregate;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;
import java.util.concurrent.Callable;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.axonframework.modelling.command.AggregateLifecycle.createNew;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Fixture tests for spawning new aggregate functionality.
 *
 * @author Milan Savic
 */
@ExtendWith(MockitoExtension.class)
class FixtureTest_SpawningNewAggregate {

    private FixtureConfiguration<Aggregate1> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Aggregate1.class);
    }

    @Test
    void fixtureWithoutRepositoryProviderInjected() {
        fixture.givenNoPriorActivity()
               .when(new CreateAggregate1Command("id", "aggregate2Id"))
               .expectEvents(new Aggregate2CreatedEvent("aggregate2Id"), new Aggregate1CreatedEvent("id"))
               .expectSuccessfulHandlerExecution();
    }

    @SuppressWarnings("unchecked")
    @Test
    void fixtureWithRepositoryProviderInjected() throws Exception {
        RepositoryProvider repositoryProvider = mock(RepositoryProvider.class);
        Repository<Aggregate2> aggregate2Repository = mock(Repository.class);
        AggregateModel<Aggregate2> aggregate2Model = AnnotatedAggregateMetaModelFactory
                .inspectAggregate(Aggregate2.class);

        when(aggregate2Repository.newInstance(any())).thenAnswer(invocation ->
                                                                         EventSourcedAggregate
                                                                                 .initialize((Callable<Aggregate2>) invocation
                                                                                                     .getArguments()[0],
                                                                                             aggregate2Model,
                                                                                             fixture.getEventStore(),
                                                                                             repositoryProvider));

        when(repositoryProvider.repositoryFor(Aggregate2.class)).thenReturn(aggregate2Repository);

        fixture.registerRepositoryProvider(repositoryProvider)
               .givenNoPriorActivity()
               .when(new CreateAggregate1Command("id", "aggregate2Id"))
               .expectEvents(new Aggregate2CreatedEvent("aggregate2Id"), new Aggregate1CreatedEvent("id"))
               .expectSuccessfulHandlerExecution();

        verify(repositoryProvider).repositoryFor(Aggregate2.class);
        verify(aggregate2Repository).newInstance(any());
    }

    private static class CreateAggregate1Command {

        private final String id;
        private final String aggregate2Id;

        private CreateAggregate1Command(String id, String aggregate2Id) {
            this.id = id;
            this.aggregate2Id = aggregate2Id;
        }

        public String getId() {
            return id;
        }

        String getAggregate2Id() {
            return aggregate2Id;
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

    private static class Aggregate2CreatedEvent {

        private final String id;

        private Aggregate2CreatedEvent(String id) {
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
            Aggregate2CreatedEvent that = (Aggregate2CreatedEvent) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    @SuppressWarnings("unused")
    private static class Aggregate1 {

        @AggregateIdentifier
        private String id;

        public Aggregate1() {
        }

        @CommandHandler
        public Aggregate1(CreateAggregate1Command command) throws Exception {
            apply(new Aggregate1CreatedEvent(command.getId()));
            createNew(Aggregate2.class, () -> new Aggregate2(command.getAggregate2Id()));
        }

        @EventSourcingHandler
        public void on(Aggregate1CreatedEvent event) throws Exception {
            this.id = event.getId();
        }
    }

    @SuppressWarnings("unused")
    private static class Aggregate2 {

        @AggregateIdentifier
        private String id;
        private String state;

        public Aggregate2() {
        }

        Aggregate2(String id) {
            apply(new Aggregate2CreatedEvent(id));
        }

        @EventSourcingHandler
        public void on(Aggregate2CreatedEvent event) {
            this.id = event.getId();
        }
    }
}
