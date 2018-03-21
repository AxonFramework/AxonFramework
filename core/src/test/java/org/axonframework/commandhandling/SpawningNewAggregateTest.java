/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling;

import org.axonframework.commandhandling.callbacks.VoidCallback;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.commandhandling.model.RepositoryProvider;
import org.axonframework.commandhandling.model.inspection.AggregateModel;
import org.axonframework.commandhandling.model.inspection.AnnotatedAggregateMetaModelFactory;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.EventSourcedAggregate;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.junit.*;

import java.util.Objects;
import java.util.concurrent.Callable;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;
import static org.axonframework.commandhandling.model.AggregateLifecycle.spawnNewAggregate;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests spawning of new aggregate from command handling of different aggregate.
 *
 * @author Milan Savic
 */
@RunWith(MockitoJUnitRunner.class)
public class SpawningNewAggregateTest {

    @Spy
    private SimpleCommandBus commandBus = new SimpleCommandBus();
    @Mock
    private Repository<Aggregate1> aggregate1Repository;
    @Mock
    private Repository<Aggregate2> aggregate2Repository;
    @Mock
    private RepositoryProvider repositoryProvider;
    @Mock
    private EventStore eventStore;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {

        AggregateModel<Aggregate1> aggregate1Model = AnnotatedAggregateMetaModelFactory
                .inspectAggregate(Aggregate1.class);
        AggregateModel<Aggregate2> aggregate2Model = AnnotatedAggregateMetaModelFactory
                .inspectAggregate(Aggregate2.class);

        when(aggregate1Repository.newInstance(any())).thenAnswer(invocation ->
                                                                         EventSourcedAggregate
                                                                                 .initialize((Callable<Aggregate1>) invocation
                                                                                                     .getArguments()[0],
                                                                                             aggregate1Model,
                                                                                             eventStore,
                                                                                             repositoryProvider));
        when(aggregate2Repository.newInstance(any())).thenAnswer(invocation ->
                                                                         EventSourcedAggregate
                                                                                 .initialize((Callable<Aggregate2>) invocation
                                                                                                     .getArguments()[0],
                                                                                             aggregate2Model,
                                                                                             eventStore,
                                                                                             repositoryProvider));

        when(repositoryProvider.repositoryFor(Aggregate2.class)).thenReturn(aggregate2Repository);

        AggregateAnnotationCommandHandler<Aggregate1> aggregate1CommandHandler = new AggregateAnnotationCommandHandler<>(
                Aggregate1.class,
                aggregate1Repository);
        AggregateAnnotationCommandHandler<Aggregate2> aggregate2CommandHandler = new AggregateAnnotationCommandHandler<>(
                Aggregate2.class,
                aggregate2Repository);
        aggregate1CommandHandler.subscribe(commandBus);
        aggregate2CommandHandler.subscribe(commandBus);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSpawningNewAggregate() throws Exception {
        commandBus.dispatch(asCommandMessage(new CreateAggregate1Command("id", "aggregate2Id")));

        verify(aggregate1Repository).newInstance(any());
        verify(repositoryProvider).repositoryFor(Aggregate2.class);
        verify(aggregate2Repository).newInstance(any());

        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);

        verify(eventStore, times(2)).publish(eventCaptor.capture());
        assertEquals(new Aggregate2CreatedEvent("aggregate2Id"), eventCaptor.getAllValues().get(0).getPayload());
        assertEquals(new Aggregate1CreatedEvent("id"), eventCaptor.getAllValues().get(1).getPayload());
    }

    @Test
    public void testSpawningNewAggregateWhenThereIsNoRepositoryForIt() {
        when(repositoryProvider.repositoryFor(Aggregate2.class)).thenReturn(null);
        commandBus.dispatch(asCommandMessage(new CreateAggregate1Command("id", "aggregate2Id")),
                            new VoidCallback<Object>() {
                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    assertEquals(
                                            "There is no configured repository for org.axonframework.commandhandling.SpawningNewAggregateTest$Aggregate2",
                                            cause.getMessage());
                                }

                                @Override
                                protected void onSuccess(CommandMessage<?> commandMessage) {
                                    fail("Expected exception");
                                }
                            });
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

        public String getAggregate2Id() {
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

        @Override
        public String toString() {
            return "Aggregate2CreatedEvent{" +
                    "id='" + id + '\'' +
                    '}';
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
            spawnNewAggregate(() -> new Aggregate2(command.getAggregate2Id()), Aggregate2.class);
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

        public Aggregate2(String id) {
            apply(new Aggregate2CreatedEvent(id));
        }

        @EventSourcingHandler
        public void on(Aggregate2CreatedEvent event) {
            this.id = event.getId();
        }
    }
}
