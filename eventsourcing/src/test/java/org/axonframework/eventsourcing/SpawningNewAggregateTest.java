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

package org.axonframework.eventsourcing;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.AggregateAnnotationCommandHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.quality.*;

import java.util.Objects;
import java.util.concurrent.Callable;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.axonframework.modelling.command.AggregateLifecycle.createNew;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests spawning of new aggregate from command handling of different aggregate.
 *
 * @author Milan Savic
 */
@ExtendWith(MockitoExtension.class)
class SpawningNewAggregateTest {

    private SimpleCommandBus commandBus;

    @Mock
    private Repository<Aggregate1> aggregate1Repository;
    @Mock
    private Repository<Aggregate2> aggregate2Repository;
    @Mock
    private RepositoryProvider repositoryProvider;
    @Mock
    private EventStore eventStore;
    private AggregateModel<Aggregate1> aggregate1Model;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        commandBus = new SimpleCommandBus();

        aggregate1Model = AnnotatedAggregateMetaModelFactory.inspectAggregate(Aggregate1.class);
        AggregateModel<Aggregate2> aggregate2Model = AnnotatedAggregateMetaModelFactory
                .inspectAggregate(Aggregate2.class);

        when(aggregate2Repository.newInstance(any())).thenAnswer(invocation ->
                                                                         EventSourcedAggregate
                                                                                 .initialize((Callable<Aggregate2>) invocation
                                                                                                     .getArguments()[0],
                                                                                             aggregate2Model,
                                                                                             eventStore,
                                                                                             repositoryProvider));

        when(repositoryProvider.repositoryFor(Aggregate2.class)).thenReturn(aggregate2Repository);

        AggregateAnnotationCommandHandler<Aggregate1> aggregate1CommandHandler =
                AggregateAnnotationCommandHandler.<Aggregate1>builder()
                                                 .aggregateType(Aggregate1.class)
                                                 .repository(aggregate1Repository)
                                                 .build();
        AggregateAnnotationCommandHandler<Aggregate2> aggregate2CommandHandler =
                AggregateAnnotationCommandHandler.<Aggregate2>builder()
                                                 .aggregateType(Aggregate2.class)
                                                 .repository(aggregate2Repository)
                                                 .build();
        aggregate1CommandHandler.subscribe(commandBus);
        aggregate2CommandHandler.subscribe(commandBus);
    }

    @SuppressWarnings("unchecked")
    @Test
    @Disabled("TODO #3070 - Revise the Repository")
    void spawningNewAggregate() throws Exception {
        initializeAggregate1Repository(repositoryProvider);
        commandBus.dispatch(asCommandMessage(new CreateAggregate1Command("id", "aggregate2Id")),
                            ProcessingContext.NONE);

        verify(aggregate1Repository).newInstance(any());
        verify(repositoryProvider).repositoryFor(Aggregate2.class);
        verify(aggregate2Repository).newInstance(any());

        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);

        verify(eventStore, times(2)).publish(eventCaptor.capture());
        assertEquals(new Aggregate2CreatedEvent("aggregate2Id"), eventCaptor.getAllValues().get(0).getPayload());
        assertEquals(new Aggregate1CreatedEvent("id"), eventCaptor.getAllValues().get(1).getPayload());
    }

    @MockitoSettings(strictness = Strictness.LENIENT)
    @Test
    @Disabled("TODO #3070 - Revise the Repository")
    void spawningNewAggregateWhenThereIsNoRepositoryForIt() throws Exception {
        initializeAggregate1Repository(repositoryProvider);
        when(repositoryProvider.repositoryFor(Aggregate2.class)).thenReturn(null);
        commandBus.dispatch(
                asCommandMessage(new CreateAggregate1Command("id", "aggregate2Id")),
                ProcessingContext.NONE
//                , (commandMessage, commandResultMessage) -> {
//                    if (commandResultMessage.isExceptional()) {
//                        Throwable cause = commandResultMessage.exceptionResult();
//                        assertTrue(cause instanceof IllegalStateException);
//                        assertEquals(
//                                "There is no configured repository for org.axonframework.eventsourcing.SpawningNewAggregateTest$Aggregate2",
//                                cause.getMessage());
//                    } else {
//                        fail("Expected exception");
//                    }
//                }
        );
    }

    @MockitoSettings(strictness = Strictness.LENIENT)
    @Test
    @Disabled("TODO #3070 - Revise the Repository")
    void spawningNewAggregateWhenThereIsNoRepositoryProviderProvided() throws Exception {
        initializeAggregate1Repository(null);
        commandBus.dispatch(
                asCommandMessage(new CreateAggregate1Command("id", "aggregate2Id")),
                ProcessingContext.NONE
//                , (commandMessage, commandResultMessage) -> {
//                    if (commandResultMessage.isExceptional()) {
//                        Throwable cause = commandResultMessage.exceptionResult();
//                        assertTrue(cause instanceof AxonConfigurationException);
//                        assertEquals(
//                                "Since repository provider is not provided, we cannot spawn a new aggregate for org.axonframework.eventsourcing.SpawningNewAggregateTest$Aggregate2",
//                                cause.getMessage());
//                    } else {
//                        fail("Expected exception");
//                    }
//                }
        );
    }

    @SuppressWarnings("unchecked")
    private void initializeAggregate1Repository(RepositoryProvider repositoryProvider) throws Exception {
        when(aggregate1Repository.newInstance(any())).thenAnswer(invocation ->
                                                                         EventSourcedAggregate
                                                                                 .initialize((Callable<Aggregate1>) invocation
                                                                                                     .getArguments()[0],
                                                                                             aggregate1Model,
                                                                                             eventStore,
                                                                                             repositoryProvider));
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
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(CreateAggregate1Command command) throws Exception {
            apply(new Aggregate1CreatedEvent(command.getId()));

            createNew(Aggregate2.class, () -> new Aggregate2(command.getAggregate2Id()));
        }

        @EventSourcingHandler
        public void on(Aggregate1CreatedEvent event) {
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
