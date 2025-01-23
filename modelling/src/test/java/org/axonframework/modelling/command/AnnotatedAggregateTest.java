/*
 * Copyright (c) 2010-2025. Axon Framework
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

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.SimpleResourceParameterResolverFactory;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AnnotatedAggregateTest {

    private static final MessageType TEST_COMMAND_TYPE = new MessageType("command");

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
        Command testPayload = new Command(ID, 2);
        CommandMessage<Object> testCommand = new GenericCommandMessage<>(TEST_COMMAND_TYPE, testPayload);
        DefaultUnitOfWork<CommandMessage<Object>> uow = DefaultUnitOfWork.startAndGet(testCommand);

        Aggregate<AggregateRoot> aggregate =
                uow.executeWithResult(() -> repository.newInstance(() -> {
                       AggregateRoot root = new AggregateRoot();
                       root.handle(testPayload);
                       return root;
                   }))
                   .getPayload();
        assertNotNull(aggregate);

        InOrder inOrder = inOrder(eventBus);
        inOrder.verify(eventBus).publish(argThat((ArgumentMatcher<EventMessage<?>>) x -> Event_1.class
                .equals(x.getPayloadType()) && ((Event_1) x.getPayload()).value == 1));
        inOrder.verify(eventBus).publish(argThat((ArgumentMatcher<EventMessage<?>>) x -> Event_1.class
                .equals(x.getPayloadType()) && ((Event_1) x.getPayload()).value == 2));
    }

    @Test
    void applyingEventInHandlerPublishesInRightOrder() {
        Command testPayload = new Command(ID, 0);
        CommandMessage<Object> testCommand = new GenericCommandMessage<>(TEST_COMMAND_TYPE, testPayload);
        DefaultUnitOfWork<CommandMessage<Object>> uow = DefaultUnitOfWork.startAndGet(testCommand);

        Aggregate<AggregateRoot> aggregate =
                uow.executeWithResult(() -> repository.newInstance(() -> {
                       AggregateRoot root = new AggregateRoot();
                       root.handle(testPayload);
                       return root;
                   }))
                   .getPayload();
        assertNotNull(aggregate);

        InOrder inOrder = inOrder(eventBus);
        inOrder.verify(eventBus).publish(argThat((ArgumentMatcher<EventMessage<?>>) x -> Event_1.class
                .equals(x.getPayloadType())));
        inOrder.verify(eventBus).publish(argThat((ArgumentMatcher<EventMessage<?>>) x -> Event_2.class
                .equals(x.getPayloadType())));
    }

    // Test for issue #1506 - https://github.com/AxonFramework/AxonFramework/issues/1506
    @Test
    void lastSequenceReturnsNullIfNoEventsHaveBeenPublishedYet() {
        final Command testPayload = new Command(ID, 0);
        CommandMessage<Object> testCommand = new GenericCommandMessage<>(TEST_COMMAND_TYPE, testPayload);
        DefaultUnitOfWork<CommandMessage<Object>> uow = DefaultUnitOfWork.startAndGet(testCommand);

        AnnotatedAggregate<AggregateRoot> testSubject =
                (AnnotatedAggregate<AggregateRoot>) uow.executeWithResult(
                        () -> repository.newInstance(AggregateRoot::new)).getPayload();

        assertNull(testSubject.lastSequence());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void conditionalApplyingEventInHandlerPublishesInRightOrder(boolean applyConditional) {
        Command_2 testPayload = new Command_2(ID, 0, applyConditional);
        CommandMessage<Object> testCommand = new GenericCommandMessage<>(TEST_COMMAND_TYPE, testPayload);
        DefaultUnitOfWork<CommandMessage<Object>> uow = DefaultUnitOfWork.startAndGet(testCommand);

        Aggregate<AggregateRoot> aggregate =
                uow.executeWithResult(() -> repository.newInstance(() -> {
                       AggregateRoot root = new AggregateRoot();
                       root.handle(testPayload, sideEffect);
                       return root;
                   }))
                   .getPayload();
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

    private record Command(String id, int value) {

    }

    private record Command_2(String id, int value, boolean condition) {

    }

    private record Event_1(String id, int value) {

    }

    @SuppressWarnings("WeakerAccess")
    private record Event_2(String id) {

    }

    @SuppressWarnings("WeakerAccess")
    private record Event_3(String id) {

    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class AggregateRoot {

        @AggregateIdentifier
        private String id;

        private int value;

        public AggregateRoot() {
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(Command command) {
            ApplyMore andThen = apply(new Event_1(command.id(), 0));
            for (int i = 0; i < command.value; i++) {
                andThen = andThen.andThenApply(() -> new Event_1(id, value + 1));
            }
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(Command_2 command, StubSideEffect sideEffect) {
            apply(new Event_1(command.id(), 0))
                    .andThenApplyIf(() -> command.condition, () -> new Event_2(command.id()))
                    .andThenApply(() -> new Event_3(id))
                    .andThen(() -> sideEffect.doSomething(command.id()))
                    .andThenIf(() -> command.condition, () -> sideEffect.doSomethingConditional(command.id()));
        }

        @EventHandler
        public void on(Event_1 event) {
            this.id = event.id();
            this.value = event.value;
            apply(new Event_2(event.id()));
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
                                    ClasspathParameterResolverFactory.forClassLoader(
                                            Thread.currentThread().getContextClassLoader()
                                    )
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
