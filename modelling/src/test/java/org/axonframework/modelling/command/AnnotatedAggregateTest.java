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
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.SimpleResourceParameterResolverFactory;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

public class AnnotatedAggregateTest {

    private final String ID = "id";
    private Repository<AggregateRoot> repository;
    private EventBus eventBus;
    private StubSideEffect sideEffect;

    @BeforeEach
    void setUp() {
        eventBus = mock(EventBus.class);
        sideEffect = mock(StubSideEffect.class);
        List<Object> resolvableResources = new ArrayList<>();
        resolvableResources.add(sideEffect);
        repository = StubRepository.builder(resolvableResources).eventBus(eventBus).build();
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
        final Command command = new Command(ID, 0);
        DefaultUnitOfWork<CommandMessage<Object>> uow = DefaultUnitOfWork.startAndGet(asCommandMessage(command));
        AnnotatedAggregate<AggregateRoot> testSubject =
                (AnnotatedAggregate<AggregateRoot>) uow.executeWithResult(
                        () -> repository.newInstance(AggregateRoot::new)).getPayload();

        assertNull(testSubject.lastSequence());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testConditionalApplyingEventInHandlerPublishesInRightOrder(boolean applyConditional) {
        Command_2 command = new Command_2(ID, 0, applyConditional);
        DefaultUnitOfWork<CommandMessage<Object>> uow = DefaultUnitOfWork.startAndGet(asCommandMessage(command));
        Aggregate<AggregateRoot> aggregate = uow.executeWithResult(() -> repository
                .newInstance(() -> new AggregateRoot(command, sideEffect))).getPayload();
        assertNotNull(aggregate);

        InOrder inOrderEvents = inOrder(eventBus);
        inOrderEvents.verify(eventBus).publish(argThat((ArgumentMatcher<EventMessage<?>>) x -> Event_1.class
                .equals(x.getPayloadType())));
        if (applyConditional) {
            inOrderEvents.verify(eventBus).publish(argThat((ArgumentMatcher<EventMessage<?>>) x -> Event_2.class
                    .equals(x.getPayloadType())));
        }
        inOrderEvents.verify(eventBus).publish(argThat((ArgumentMatcher<EventMessage<?>>) x -> Event_3.class
                .equals(x.getPayloadType())));

        InOrder inOrderSideEffect = inOrder(sideEffect);
        inOrderSideEffect.verify(sideEffect).doSomething(eq(ID));
        if (applyConditional) {
            inOrderSideEffect.verify(sideEffect).doSomethingConditional(ID);
        }
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

    private static class Command_2 {

        private final String id;
        private final int value;
        private final boolean condition;

        private Command_2(String id, int value, boolean condition) {
            this.id = id;
            this.value = value;
            this.condition = condition;
        }

        public String getId() {
            return id;
        }

        public int getValue() {
            return value;
        }

        public boolean condition() {
            return condition;
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

    @SuppressWarnings("WeakerAccess")
    private static class Event_3 {

        private final String id;

        Event_3(String id) {
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

        @CommandHandler
        public AggregateRoot(Command_2 command, StubSideEffect sideEffect) {
            apply(new Event_1(command.getId(), 0))
                    .andThenApplyIf(() -> command.condition, () -> new Event_2(command.getId()))
                    .andThenApply(() -> new Event_3(id))
                    .andThen(() -> sideEffect.doSomething(command.getId()))
                    .andThenIf(() -> command.condition, () -> sideEffect.doSomethingConditional(command.getId()));
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

        @EventHandler
        public void on(Event_3 event) {
        }
    }

    private static class StubRepository extends AbstractRepository<AggregateRoot, Aggregate<AggregateRoot>> {

        private final EventBus eventBus;

        public static Builder builder(Iterable<?> resources) {
            return (Builder) new Builder()
                    .parameterResolverFactory(
                            MultiParameterResolverFactory.ordered(
                                    new SimpleResourceParameterResolverFactory(resources),
                                    ClasspathParameterResolverFactory.forClassLoader(Thread.currentThread().getContextClassLoader())
                            ));
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

    public interface StubSideEffect {
        void doSomething(String id);

        void doSomethingConditional(String id);
    }
}
