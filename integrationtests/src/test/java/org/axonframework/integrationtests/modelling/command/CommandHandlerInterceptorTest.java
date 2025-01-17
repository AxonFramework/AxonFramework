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

package org.axonframework.integrationtests.modelling.command;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
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
import org.axonframework.messaging.ClassBasedMessageNameResolver;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageNameResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.command.AggregateAnnotationCommandHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.CommandHandlerInterceptor;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.modelling.command.EntityId;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for annotated command handler interceptor on aggregate / child entities.
 *
 * @author Milan Savic
 */
class CommandHandlerInterceptorTest {

    private static final QualifiedName TEST_COMMAND_NAME = new QualifiedName("test", "command", "0.0.1");

    private CommandGateway commandGateway;
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        eventStore = spy(EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build());
        Repository<MyAggregate> myAggregateRepository = EventSourcingRepository.builder(MyAggregate.class)
                                                                               .eventStore(eventStore)
                                                                               .build();
        CommandBus commandBus = new SimpleCommandBus();
        MessageNameResolver nameResolver = new ClassBasedMessageNameResolver();
        commandGateway = new DefaultCommandGateway(commandBus, nameResolver);
        AggregateAnnotationCommandHandler<MyAggregate> myAggregateCommandHandler =
                AggregateAnnotationCommandHandler.<MyAggregate>builder()
                                                 .aggregateType(MyAggregate.class)
                                                 .repository(myAggregateRepository)
                                                 .build();

        myAggregateCommandHandler.subscribe(commandBus);
    }

    @SuppressWarnings("unchecked")
    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void interceptor() {
        CommandMessage<CreateMyAggregateCommand> createCommand =
                new GenericCommandMessage<>(TEST_COMMAND_NAME, new CreateMyAggregateCommand("id"));
        CommandMessage<UpdateMyAggregateStateCommand> updateCommand =
                new GenericCommandMessage<>(TEST_COMMAND_NAME, new UpdateMyAggregateStateCommand("id", "state"));

        commandGateway.sendAndWait(createCommand);
        String result = commandGateway.sendAndWait(updateCommand, String.class);

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
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void interceptorWithChainProceeding() {
        CommandMessage<CreateMyAggregateCommand> createCommand =
                new GenericCommandMessage<>(TEST_COMMAND_NAME, new CreateMyAggregateCommand("id"));
        CommandMessage<ClearMyAggregateStateCommand> updateCommand =
                new GenericCommandMessage<>(TEST_COMMAND_NAME, new ClearMyAggregateStateCommand("id", true));

        commandGateway.sendAndWait(createCommand);
        commandGateway.sendAndWait(updateCommand);

        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);
        verify(eventStore, times(3)).publish(eventCaptor.capture());
        assertEquals(new MyAggregateCreatedEvent("id"), eventCaptor.getAllValues().get(0).getPayload());
        assertEquals(new AnyCommandInterceptedEvent(ClearMyAggregateStateCommand.class.getName()),
                     eventCaptor.getAllValues().get(1).getPayload());
        assertEquals(new MyAggregateStateClearedEvent("id"), eventCaptor.getAllValues().get(2).getPayload());
    }

    @SuppressWarnings("unchecked")
    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void interceptorWithoutChainProceeding() {
        CommandMessage<CreateMyAggregateCommand> createCommand =
                new GenericCommandMessage<>(TEST_COMMAND_NAME, new CreateMyAggregateCommand("id"));
        CommandMessage<ClearMyAggregateStateCommand> updateCommand =
                new GenericCommandMessage<>(TEST_COMMAND_NAME, new ClearMyAggregateStateCommand("id", false));

        commandGateway.sendAndWait(createCommand);
        commandGateway.sendAndWait(updateCommand);

        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);
        verify(eventStore, times(2)).publish(eventCaptor.capture());
        assertEquals(new MyAggregateCreatedEvent("id"), eventCaptor.getAllValues().get(0).getPayload());
        assertEquals(new MyAggregateStateNotClearedEvent("id"), eventCaptor.getAllValues().get(1).getPayload());
    }

    @SuppressWarnings("unchecked")
    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void interceptorWithNestedEntity() {
        CommandMessage<CreateMyAggregateCommand> createCommand =
                new GenericCommandMessage<>(TEST_COMMAND_NAME, new CreateMyAggregateCommand("id"));
        CommandMessage<MyNestedCommand> nestedCommand =
                new GenericCommandMessage<>(TEST_COMMAND_NAME, new MyNestedCommand("id", "state"));

        commandGateway.sendAndWait(createCommand);
        commandGateway.sendAndWait(nestedCommand);

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
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void interceptorWithNestedNestedEntity() {
        CommandMessage<CreateMyAggregateCommand> createCommand =
                new GenericCommandMessage<>(TEST_COMMAND_NAME, new CreateMyAggregateCommand("id"));
        CommandMessage<MyNestedNestedCommand> nestedCommand =
                new GenericCommandMessage<>(TEST_COMMAND_NAME, new MyNestedNestedCommand("id", "state"));

        commandGateway.sendAndWait(createCommand);
        commandGateway.sendAndWait(nestedCommand);

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
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void interceptorThrowingAnException() {
        CommandMessage<CreateMyAggregateCommand> createCommand =
                new GenericCommandMessage<>(TEST_COMMAND_NAME, new CreateMyAggregateCommand("id"));
        CommandMessage<InterceptorThrowingCommand> interceptorCommand =
                new GenericCommandMessage<>(TEST_COMMAND_NAME, new InterceptorThrowingCommand("id"));

        commandGateway.sendAndWait(createCommand);
        assertThrows(InterceptorException.class, () -> commandGateway.sendAndWait(interceptorCommand));
        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);
        verify(eventStore, times(1)).publish(eventCaptor.capture());
        assertEquals(new MyAggregateCreatedEvent("id"), eventCaptor.getAllValues().get(0).getPayload());
    }

    private record CreateMyAggregateCommand(String id) {

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

    private record ClearMyAggregateStateCommand(@TargetAggregateIdentifier String id, boolean proceed) {

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

    public record InterceptorThrowingCommand(@TargetAggregateIdentifier String id) {

    }

    private record MyAggregateCreatedEvent(String id) {

    }

    private record MyAggregateStateUpdatedEvent(@TargetAggregateIdentifier String id, String state) {

    }

    private record MyAggregateStateClearedEvent(String id) {

    }

    private record MyAggregateStateNotClearedEvent(String id) {

    }

    private record MyNestedEvent(String id, String state) {

    }

    private record MyNestedNestedEvent(String id, String state) {

    }

    private record InterceptorThrewEvent(String id) {

    }

    private record AnyCommandInterceptedEvent(String id) {

    }

    private record AnyCommandMatchingPatternInterceptedEvent(String id) {

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
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(CreateMyAggregateCommand command) {
            apply(new MyAggregateCreatedEvent(command.id()));
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
            this.id = event.id();
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
            this.state = event.state();
        }

        @CommandHandlerInterceptor
        public void intercept(ClearMyAggregateStateCommand command, InterceptorChain interceptorChain)
                throws Exception {
            if (command.proceed()) {
                interceptorChain.proceedSync();
            } else {
                apply(new MyAggregateStateNotClearedEvent(command.id()));
            }
        }

        @CommandHandler
        public void handle(ClearMyAggregateStateCommand command) {
            apply(new MyAggregateStateClearedEvent(command.id()));
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
            apply(new InterceptorThrewEvent(command.id()));
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
            this.state = event.state();
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
