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

package org.axonframework.integrationtests.modelling.command;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.modelling.command.AggregateAnnotationCommandHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.CommandHandlerInterceptor;
import org.axonframework.modelling.command.EntityId;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Objects;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for annotated command handler interceptor on aggregate / child entities.
 *
 * @author Milan Savic
 */
class CommandHandlerInterceptorTest {

    private CommandGateway commandGateway;
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        eventStore = spy(EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build());
        Repository<MyAggregate> myAggregateRepository = EventSourcingRepository.builder(MyAggregate.class)
                                                                               .eventStore(eventStore)
                                                                               .build();
        CommandBus commandBus = SimpleCommandBus.builder().build();
        commandGateway = DefaultCommandGateway.builder().commandBus(commandBus).build();
        AggregateAnnotationCommandHandler<MyAggregate> myAggregateCommandHandler =
                AggregateAnnotationCommandHandler.<MyAggregate>builder()
                        .aggregateType(MyAggregate.class)
                        .repository(myAggregateRepository)
                        .build();

        myAggregateCommandHandler.subscribe(commandBus);
    }

    @SuppressWarnings("unchecked")
    @Test
    void interceptor() {
        commandGateway.sendAndWait(asCommandMessage(new CreateMyAggregateCommand("id")));
        String result = commandGateway
                .sendAndWait(asCommandMessage(new UpdateMyAggregateStateCommand("id", "state")));

        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);

        verify(eventStore, times(3)).publish(eventCaptor.capture());
        assertEquals(new MyAggregateCreatedEvent("id"), eventCaptor.getAllValues().get(0).getPayload());
        assertEquals(new AnyCommandInterceptedEvent(UpdateMyAggregateStateCommand.class.getName()),
                     eventCaptor.getAllValues().get(1).getPayload());
        assertEquals(new MyAggregateStateUpdatedEvent("id", "state intercepted"),
                     eventCaptor.getAllValues().get(2).getPayload());

        assertEquals("aggregateUpdateResult", result);
    }

    @SuppressWarnings("unchecked")
    @Test
    void interceptorWithChainProceeding() {
        commandGateway.sendAndWait(asCommandMessage(new CreateMyAggregateCommand("id")));
        commandGateway.sendAndWait(asCommandMessage(new ClearMyAggregateStateCommand("id", true)));

        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);
        verify(eventStore, times(3)).publish(eventCaptor.capture());
        assertEquals(new MyAggregateCreatedEvent("id"), eventCaptor.getAllValues().get(0).getPayload());
        assertEquals(new AnyCommandInterceptedEvent(ClearMyAggregateStateCommand.class.getName()),
                     eventCaptor.getAllValues().get(1).getPayload());
        assertEquals(new MyAggregateStateClearedEvent("id"), eventCaptor.getAllValues().get(2).getPayload());
    }

    @SuppressWarnings("unchecked")
    @Test
    void interceptorWithoutChainProceeding() {
        commandGateway.sendAndWait(asCommandMessage(new CreateMyAggregateCommand("id")));
        commandGateway.sendAndWait(asCommandMessage(new ClearMyAggregateStateCommand("id", false)));

        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);
        verify(eventStore, times(2)).publish(eventCaptor.capture());
        assertEquals(new MyAggregateCreatedEvent("id"), eventCaptor.getAllValues().get(0).getPayload());
        assertEquals(new MyAggregateStateNotClearedEvent("id"), eventCaptor.getAllValues().get(1).getPayload());
    }

    @SuppressWarnings("unchecked")
    @Test
    void interceptorWithNestedEntity() {
        commandGateway.sendAndWait(asCommandMessage(new CreateMyAggregateCommand("id")));
        commandGateway.sendAndWait(asCommandMessage(new MyNestedCommand("id", "state")));

        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);
        verify(eventStore, times(4)).publish(eventCaptor.capture());
        assertEquals(new MyAggregateCreatedEvent("id"), eventCaptor.getAllValues().get(0).getPayload());
        assertEquals(new AnyCommandMatchingPatternInterceptedEvent(MyNestedCommand.class.getName()),
                     eventCaptor.getAllValues().get(1).getPayload());
        assertEquals(new AnyCommandInterceptedEvent(MyNestedCommand.class.getName()),
                     eventCaptor.getAllValues().get(2).getPayload());
        assertEquals(new MyNestedEvent("id", "state intercepted"), eventCaptor.getAllValues().get(3).getPayload());
    }

    @SuppressWarnings("unchecked")
    @Test
    void interceptorWithNestedNestedEntity() {
        commandGateway.sendAndWait(asCommandMessage(new CreateMyAggregateCommand("id")));
        commandGateway.sendAndWait(asCommandMessage(new MyNestedNestedCommand("id", "state")));

        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);
        verify(eventStore, times(6)).publish(eventCaptor.capture());
        assertEquals(new MyAggregateCreatedEvent("id"), eventCaptor.getAllValues().get(0).getPayload());
        assertEquals(new AnyCommandMatchingPatternInterceptedEvent(MyNestedNestedCommand.class.getName()),
                     eventCaptor.getAllValues().get(1).getPayload());
        assertEquals(new AnyCommandInterceptedEvent(MyNestedNestedCommand.class.getName()),
                     eventCaptor.getAllValues().get(2).getPayload());
        assertEquals(new AnyCommandInterceptedEvent("StaticNestedNested" + MyNestedNestedCommand.class.getName()),
                     eventCaptor.getAllValues().get(3).getPayload());
        assertEquals(new AnyCommandInterceptedEvent("NestedNested" + MyNestedNestedCommand.class.getName()),
                     eventCaptor.getAllValues().get(4).getPayload());
        assertEquals(new MyNestedNestedEvent("id", "state parent intercepted intercepted"),
                     eventCaptor.getAllValues().get(5).getPayload());
    }

    @Test
    void interceptorWithNonVoidReturnType() {
        EventSourcingRepository.Builder<MyAggregateWithInterceptorReturningNonVoid> builder =
                EventSourcingRepository.builder(MyAggregateWithInterceptorReturningNonVoid.class)
                        .eventStore(eventStore);

        assertThrows(AxonConfigurationException.class, builder::build);
    }

    @Test
    void interceptorWithDeclaredChainAllowedToDeclareNonVoidReturnType() {
        EventSourcingRepository.builder(MyAggregateWithDeclaredInterceptorChainInterceptorReturningNonVoid.class)
                .eventStore(eventStore)
                .build();
    }

    @SuppressWarnings("unchecked")
    @Test
    void interceptorThrowingAnException() {
        commandGateway.sendAndWait(asCommandMessage(new CreateMyAggregateCommand("id")));
        assertThrows(
                InterceptorException.class,
                () -> commandGateway.sendAndWait(asCommandMessage(new InterceptorThrowingCommand("id")))
        );
        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);
        verify(eventStore, times(1)).publish(eventCaptor.capture());
        assertEquals(new MyAggregateCreatedEvent("id"), eventCaptor.getAllValues().get(0).getPayload());
    }

    private static class CreateMyAggregateCommand {

        private final String id;

        private CreateMyAggregateCommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class UpdateMyAggregateStateCommand {

        @TargetAggregateIdentifier
        private final String id;
        private String state;

        private UpdateMyAggregateStateCommand(String id, String state) {
            this.id = id;
            this.state = state;
        }

        public String getId() {
            return id;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }
    }

    private static class ClearMyAggregateStateCommand {

        @TargetAggregateIdentifier
        private final String id;
        private final boolean proceed;

        private ClearMyAggregateStateCommand(String id, boolean proceed) {
            this.id = id;
            this.proceed = proceed;
        }

        public String getId() {
            return id;
        }

        public boolean isProceed() {
            return proceed;
        }
    }

    private static class MyNestedCommand {

        @TargetAggregateIdentifier
        private final String id;
        private String state;

        private MyNestedCommand(String id, String state) {
            this.id = id;
            this.state = state;
        }

        public String getId() {
            return id;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }
    }

    private static class MyNestedNestedCommand {

        @TargetAggregateIdentifier
        private final String id;
        private String state;

        private MyNestedNestedCommand(String id, String state) {
            this.id = id;
            this.state = state;
        }

        public String getId() {
            return id;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }
    }

    public static class InterceptorThrowingCommand {

        @TargetAggregateIdentifier
        private final String id;

        public InterceptorThrowingCommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class MyAggregateCreatedEvent {

        private final String id;

        private MyAggregateCreatedEvent(String id) {
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
            MyAggregateCreatedEvent that = (MyAggregateCreatedEvent) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class MyAggregateStateUpdatedEvent {

        @TargetAggregateIdentifier
        private final String id;
        private final String state;

        private MyAggregateStateUpdatedEvent(String id, String state) {
            this.id = id;
            this.state = state;
        }

        public String getId() {
            return id;
        }

        public String getState() {
            return state;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MyAggregateStateUpdatedEvent that = (MyAggregateStateUpdatedEvent) o;
            return Objects.equals(id, that.id) &&
                    Objects.equals(state, that.state);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, state);
        }
    }

    private static class MyAggregateStateClearedEvent {

        private final String id;

        private MyAggregateStateClearedEvent(String id) {
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
            MyAggregateStateClearedEvent that = (MyAggregateStateClearedEvent) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class MyAggregateStateNotClearedEvent {

        private final String id;

        private MyAggregateStateNotClearedEvent(String id) {
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
            MyAggregateStateNotClearedEvent that = (MyAggregateStateNotClearedEvent) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class MyNestedEvent {

        private final String id;
        private final String state;

        private MyNestedEvent(String id, String state) {
            this.id = id;
            this.state = state;
        }

        public String getId() {
            return id;
        }

        public String getState() {
            return state;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MyNestedEvent that = (MyNestedEvent) o;
            return Objects.equals(id, that.id) &&
                    Objects.equals(state, that.state);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, state);
        }
    }

    private static class MyNestedNestedEvent {

        private final String id;
        private final String state;

        private MyNestedNestedEvent(String id, String state) {
            this.id = id;
            this.state = state;
        }

        public String getId() {
            return id;
        }

        public String getState() {
            return state;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MyNestedNestedEvent that = (MyNestedNestedEvent) o;
            return Objects.equals(id, that.id) &&
                    Objects.equals(state, that.state);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, state);
        }
    }

    private static class InterceptorThrewEvent {

        private final String id;

        private InterceptorThrewEvent(String id) {
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
            InterceptorThrewEvent that = (InterceptorThrewEvent) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class AnyCommandInterceptedEvent {

        private final String id;

        private AnyCommandInterceptedEvent(String id) {
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
            AnyCommandInterceptedEvent that = (AnyCommandInterceptedEvent) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class AnyCommandMatchingPatternInterceptedEvent {

        private final String id;

        private AnyCommandMatchingPatternInterceptedEvent(String id) {
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
            AnyCommandMatchingPatternInterceptedEvent that = (AnyCommandMatchingPatternInterceptedEvent) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    @SuppressWarnings("unused")
    private static class MyAggregate {

        @AggregateIdentifier
        private String id;
        private String state;
        @AggregateMember
        private MyNestedEntity myNestedEntity;

        public MyAggregate() {
            // do nothing
        }

        @CommandHandler
        public MyAggregate(CreateMyAggregateCommand command) {
            apply(new MyAggregateCreatedEvent(command.getId()));
        }

        @CommandHandlerInterceptor
        public void interceptAll(Object command) {
            apply(new AnyCommandInterceptedEvent(command.getClass().getName()));
        }

        @CommandHandlerInterceptor(commandNamePattern = ".*Nested.*")
        public void interceptAllMatchingPattern(Object command, InterceptorChain interceptorChain) throws Exception {
            apply(new AnyCommandMatchingPatternInterceptedEvent(command.getClass().getName()));
            interceptorChain.proceedSync();
        }

        @EventSourcingHandler
        public void on(MyAggregateCreatedEvent event) {
            this.id = event.getId();
            myNestedEntity = new MyNestedEntity(id);
        }

        @CommandHandlerInterceptor
        public void intercept(UpdateMyAggregateStateCommand command) {
            command.setState(command.getState() + " intercepted");
        }

        @CommandHandler
        public String handle(UpdateMyAggregateStateCommand command) {
            apply(new MyAggregateStateUpdatedEvent(command.getId(), command.getState()));
            return "aggregateUpdateResult";
        }

        @EventSourcingHandler
        public void on(MyAggregateStateUpdatedEvent event) {
            this.state = event.getState();
        }

        @CommandHandlerInterceptor
        public void intercept(ClearMyAggregateStateCommand command, InterceptorChain interceptorChain)
                throws Exception {
            if (command.isProceed()) {
                interceptorChain.proceedSync();
            } else {
                apply(new MyAggregateStateNotClearedEvent(command.getId()));
            }
        }

        @CommandHandler
        public void handle(ClearMyAggregateStateCommand command) {
            apply(new MyAggregateStateClearedEvent(command.getId()));
        }

        @EventSourcingHandler
        public void on(MyAggregateStateClearedEvent event) {
            this.state = "";
        }

        @EventSourcingHandler
        public void on(MyAggregateStateNotClearedEvent event) {
            // do nothing
        }

        @CommandHandlerInterceptor
        public void interceptChildCommand(MyNestedNestedCommand command) {
            command.setState(command.getState() + " parent intercepted");
        }

        @CommandHandlerInterceptor
        public void intercept(InterceptorThrowingCommand command) {
            throw new InterceptorException();
        }

        @CommandHandler
        public void handle(InterceptorThrowingCommand command) {
            apply(new InterceptorThrewEvent(command.getId()));
        }

        @EventSourcingHandler
        public void on(InterceptorThrewEvent event) {
            // do nothing
        }
    }

    @SuppressWarnings("unused")
    private static class MyNestedEntity {

        @EntityId
        private final String id;
        private String state;
        @AggregateMember
        private MyNestedNestedEntity myNestedNestedEntity;

        private MyNestedEntity(String id) {
            this.id = id;
            myNestedNestedEntity = new MyNestedNestedEntity(id);
        }

        @CommandHandlerInterceptor
        public void intercept(MyNestedCommand command) {
            command.setState(command.getState() + " intercepted");
        }

        @CommandHandler
        public void handle(MyNestedCommand command) {
            apply(new MyNestedEvent(command.getId(), command.getState()));
        }

        @EventSourcingHandler
        public void on(MyNestedEvent event) {
            this.state = event.getState();
        }
    }

    @SuppressWarnings("unused")
    private static class MyNestedNestedEntity {

        @EntityId
        private final String id;
        private String state;

        @CommandHandlerInterceptor
        public static void interceptAll(Object command, InterceptorChain chain) throws Exception {
            apply(new AnyCommandInterceptedEvent("StaticNestedNested" + command.getClass().getName()));
            chain.proceedSync();
        }

        private MyNestedNestedEntity(String id) {
            this.id = id;
        }

        @CommandHandlerInterceptor
        public void interceptAll(Object command) {
            apply(new AnyCommandInterceptedEvent("NestedNested" + command.getClass().getName()));
        }

        @CommandHandlerInterceptor
        public void intercept(MyNestedNestedCommand command) {
            command.setState(command.getState() + " intercepted");
        }

        @CommandHandler
        public void handle(MyNestedNestedCommand command) {
            apply(new MyNestedNestedEvent(command.getId(), command.getState()));
        }

        @EventSourcingHandler
        public void on(MyNestedNestedEvent event) {
            this.state = event.state;
        }
    }

    @SuppressWarnings("unused")
    private static class MyAggregateWithInterceptorReturningNonVoid {

        @CommandHandlerInterceptor
        public Object intercept() {
            return new Object();
        }
    }

    @SuppressWarnings("unused")
    private static class MyAggregateWithDeclaredInterceptorChainInterceptorReturningNonVoid {

        @CommandHandlerInterceptor
        public Object intercept(InterceptorChain chain) {
            return new Object();
        }
    }

    private static class InterceptorException extends RuntimeException {

    }
}
