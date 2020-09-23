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

package org.axonframework.modelling.command;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.concurrent.Callable;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AnnotatedAggregateTest {

    private final String ID = "id";
    private Repository<AggregateRoot> repository;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = mock(EventBus.class);
        repository = StubRepository.builder().eventBus(eventBus).build();
    }

    // Tests for issue #1248 - https://github.com/AxonFramework/AxonFramework/issues/1248
    @Test
    void applyingMultipleEventsInAndThenPublishesWithRightState() {
        Command command = new Command(ID, 2);
        DefaultUnitOfWork<CommandMessage<Object>> uow = DefaultUnitOfWork.startAndGet(asCommandMessage(command));
        Aggregate<AggregateRoot> aggregate = uow.executeWithResult(() -> repository
                .newInstance(() -> new AggregateRoot(command))).getPayload();
        assertNotNull(aggregate);

        InOrder inOrder = inOrder(eventBus);
        inOrder.verify(eventBus).publish(argThat((ArgumentMatcher<EventMessage<?>>) x -> Event_1.class
                .equals(x.getPayloadType()) && ((Event_1) x.getPayload()).value == 1));
        inOrder.verify(eventBus).publish(argThat((ArgumentMatcher<EventMessage<?>>) x -> Event_1.class
                .equals(x.getPayloadType()) && ((Event_1) x.getPayload()).value == 2));
    }

    @Test
    void testApplyingEventInHandlerPublishesInRightOrder() {
        Command command = new Command(ID, 0);
        DefaultUnitOfWork<CommandMessage<Object>> uow = DefaultUnitOfWork.startAndGet(asCommandMessage(command));
        Aggregate<AggregateRoot> aggregate = uow.executeWithResult(() -> repository
                .newInstance(() -> new AggregateRoot(command))).getPayload();
        assertNotNull(aggregate);

        InOrder inOrder = inOrder(eventBus);
        inOrder.verify(eventBus).publish(argThat((ArgumentMatcher<EventMessage<?>>) x -> Event_1.class
                .equals(x.getPayloadType())));
        inOrder.verify(eventBus).publish(argThat((ArgumentMatcher<EventMessage<?>>) x -> Event_2.class
                .equals(x.getPayloadType())));
    }

    // Test for issue #1506 - https://github.com/AxonFramework/AxonFramework/issues/1506
    @Test
    void testLastSequenceReturnsNullIfNoEventsHaveBeenPublishedYet() {
        AnnotatedAggregate<AggregateRoot> testSubject = AnnotatedAggregate.initialize(
                new AggregateRoot(), AnnotatedAggregateMetaModelFactory.inspectAggregate(AggregateRoot.class), eventBus
        );

        assertNull(testSubject.lastSequence());
    }

    private static class Command {

        private final String id;
        private final int value;

        private Command(String id, int value) {
            this.id = id;
            this.value = value;
        }

        public String getId() {
            return id;
        }

        public int getValue() {
            return value;
        }
    }

    private static class Event_1 {

        private final String id;
        private final int value;

        private Event_1(String id, int value) {
            this.id = id;
            this.value = value;
        }

        public String getId() {
            return id;
        }

        public int getValue() {
            return value;
        }
    }

    @SuppressWarnings("WeakerAccess")
    private static class Event_2 {

        private final String id;

        Event_2(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class AggregateRoot {

        @AggregateIdentifier
        private String id;

        private int value;

        public AggregateRoot() {
        }

        @CommandHandler
        public AggregateRoot(Command command) {
            ApplyMore andThen = apply(new Event_1(command.getId(), 0));
            for (int i = 0; i < command.value; i++) {
                andThen = andThen.andThenApply(() -> new Event_1(id, value + 1));
            }
        }

        @EventHandler
        public void on(Event_1 event) {
            this.id = event.getId();
            this.value = event.value;
            apply(new Event_2(event.getId()));
        }

        @EventHandler
        public void on(Event_2 event) {
        }
    }

    private static class StubRepository extends AbstractRepository<AggregateRoot, Aggregate<AggregateRoot>> {

        private final EventBus eventBus;

        public static Builder builder() {
            return new Builder();
        }

        private StubRepository(Builder builder) {
            super(builder);
            this.eventBus = builder.eventBus;
        }

        @Override
        protected Aggregate<AggregateRoot> doCreateNew(Callable<AggregateRoot> factoryMethod) throws Exception {
            return AnnotatedAggregate.initialize(factoryMethod, aggregateModel(), eventBus);
        }

        @Override
        protected void doSave(Aggregate<AggregateRoot> aggregate) {

        }

        @Override
        protected Aggregate<AggregateRoot> doLoad(String aggregateIdentifier, Long expectedVersion) {
            return null;
        }

        @Override
        protected void doDelete(Aggregate<AggregateRoot> aggregate) {

        }

        private static class Builder extends AbstractRepository.Builder<AggregateRoot> {

            private EventBus eventBus;

            private Builder() {
                super(AggregateRoot.class);
            }

            public Builder eventBus(EventBus eventBus) {
                this.eventBus = eventBus;
                return this;
            }

            public StubRepository build() {
                return new StubRepository(this);
            }
        }
    }
}
