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

package org.axonframework.integrationtests.testsuite.student;


import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.annotations.CommandHandler;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.integrationtests.testsuite.student.commands.ChangeStudentNameCommand;
import org.axonframework.integrationtests.testsuite.student.events.StudentNameChangedEvent;
import org.axonframework.integrationtests.testsuite.student.state.Student;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageDispatchInterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.annotations.InjectEntity;
import org.axonframework.serialization.Converter;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests whether stateful command handling components can process commands with a single entity.
 *
 * @author Mitchell Herrijgers
 */
class SingleEntityCommandHandlingComponentIT extends AbstractCommandHandlingStudentIT {

    private final String student1 = createId("student-1");
    private final String student2 = createId("student-2");

    @Test
    void canHandleCommandThatTargetsOneEntityUsingStateManager() {
        registerCommandHandlers(handlerPhase -> handlerPhase.commandHandler(
                new QualifiedName(ChangeStudentNameCommand.class),
                c -> (command, context) -> {
                    EventAppender eventAppender = EventAppender.forContext(context);
                    ChangeStudentNameCommand payload =
                            command.payloadAs(ChangeStudentNameCommand.class, c.getComponent(Converter.class));
                    StateManager state = context.component(StateManager.class);
                    Student student = state.loadEntity(Student.class, payload.id(), context).join();
                    eventAppender.append(new StudentNameChangedEvent(student.getId(), payload.name()));
                    // Entity through magic of repository automatically updated
                    assertEquals(student.getName(), payload.name());
                    return MessageStream.just(SUCCESSFUL_COMMAND_RESULT).cast();
                }
        ));
        startApp();

        changeStudentName(student1, "name-1");
        verifyStudentName(student1, "name-1");
        changeStudentName(student1, "name-2");
        verifyStudentName(student1, "name-2");
        changeStudentName(student1, "name-3");
        verifyStudentName(student1, "name-3");
        changeStudentName(student1, "name-4");
        verifyStudentName(student1, "name-4");

        changeStudentName(student2, "name-5");
        verifyStudentName(student1, "name-4");
        verifyStudentName(student2, "name-5");
    }

    @Test
    void canHandleCommandThatTargetsOneModelViaStateManagerParameter() {
        registerCommandHandlers(handlerPhase -> handlerPhase.annotatedCommandHandlingComponent(
                c -> new SingleModelAnnotatedCommandHandler()
        ));
        startApp();

        changeStudentName(student1, "name-1");
        verifyStudentName(student1, "name-1");

        changeStudentName(student1, "name-2");
        verifyStudentName(student1, "name-2");
    }

    @Test
    void dispatchInterceptorsModifyCommandMessage() {
        // given
        MessageDispatchInterceptor<Message> dispatchInterceptor1 = new AddMetadataInterceptor<>("dispatch1", "value");
        MessageDispatchInterceptor<Message> dispatchInterceptor2 = new AddMetadataInterceptor<>("dispatch2", "value");

        AtomicInteger handlerInvocations = new AtomicInteger(0);

        registerCommandDispatchInterceptor(dispatchInterceptor1);
        registerCommandDispatchInterceptor(dispatchInterceptor2);

        registerCommandHandlers(handlerPhase -> handlerPhase.commandHandler(
                new QualifiedName(ChangeStudentNameCommand.class),
                c -> (command, context) -> {
                    handlerInvocations.incrementAndGet();

                    // Verify that dispatch interceptors added metadata to the command
                    assertTrue(command.metadata().containsKey("dispatch1"),
                               "Expected dispatch1 interceptor to add metadata to command");
                    assertTrue(command.metadata().containsKey("dispatch2"),
                               "Expected dispatch2 interceptor to add metadata to command");

                    EventAppender eventAppender = EventAppender.forContext(context);
                    ChangeStudentNameCommand payload =
                            command.payloadAs(ChangeStudentNameCommand.class, c.getComponent(Converter.class));
                    StateManager state = context.component(StateManager.class);
                    Student student = state.loadEntity(Student.class, payload.id(), context).join();
                    eventAppender.append(new StudentNameChangedEvent(student.getId(), payload.name()));
                    return MessageStream.just(SUCCESSFUL_COMMAND_RESULT).cast();
                }
        ));

        startApp();

        // when
        changeStudentName(student1, "name-1");

        // then
        assertThat(handlerInvocations.get()).isEqualTo(1);
        verifyStudentName(student1, "name-1");
    }

    @Test
    void dispatchInterceptorsAreInvokedForEveryCommand() {
        // given
        AtomicInteger dispatchCounter = new AtomicInteger(0);
        MessageDispatchInterceptor<Message> countingInterceptor = (message, context, chain) -> {
            dispatchCounter.incrementAndGet();
            return chain.proceed(message, context);
        };

        registerCommandDispatchInterceptor(countingInterceptor);

        registerCommandHandlers(handlerPhase -> handlerPhase.commandHandler(
                new QualifiedName(ChangeStudentNameCommand.class),
                c -> (command, context) -> {
                    EventAppender eventAppender = EventAppender.forContext(context);
                    ChangeStudentNameCommand payload =
                            command.payloadAs(ChangeStudentNameCommand.class, c.getComponent(Converter.class));
                    StateManager state = context.component(StateManager.class);
                    Student student = state.loadEntity(Student.class, payload.id(), context).join();
                    eventAppender.append(new StudentNameChangedEvent(student.getId(), payload.name()));
                    return MessageStream.just(SUCCESSFUL_COMMAND_RESULT).cast();
                }
        ));

        startApp();

        // when
        changeStudentName(student1, "name-1");
        changeStudentName(student1, "name-2");
        changeStudentName(student1, "name-3");

        // then
        assertThat(dispatchCounter.get()).isEqualTo(3);
    }

    @Test
    void handlerInterceptorsModifyCommandMessage() {
        // given
        MessageHandlerInterceptor<CommandMessage> handlerInterceptor1 = new AddMetadataInterceptor<>("handler1",
                                                                                                     "value");
        MessageHandlerInterceptor<CommandMessage> handlerInterceptor2 = new AddMetadataInterceptor<>("handler2",
                                                                                                     "value");

        AtomicInteger handlerInvocations = new AtomicInteger(0);

        registerCommandHandlerInterceptor(handlerInterceptor1);
        registerCommandHandlerInterceptor(handlerInterceptor2);

        registerCommandHandlers(handlerPhase -> handlerPhase.commandHandler(
                new QualifiedName(ChangeStudentNameCommand.class),
                c -> (command, context) -> {
                    handlerInvocations.incrementAndGet();

                    // Verify that handler interceptors added metadata to the command
                    assertTrue(command.metadata().containsKey("handler1"),
                               "Expected handler1 interceptor to add metadata to command");
                    assertTrue(command.metadata().containsKey("handler2"),
                               "Expected handler2 interceptor to add metadata to command");

                    EventAppender eventAppender = EventAppender.forContext(context);
                    ChangeStudentNameCommand payload =
                            command.payloadAs(ChangeStudentNameCommand.class, c.getComponent(Converter.class));
                    StateManager state = context.component(StateManager.class);
                    Student student = state.loadEntity(Student.class, payload.id(), context).join();
                    eventAppender.append(new StudentNameChangedEvent(student.getId(), payload.name()));
                    return MessageStream.just(SUCCESSFUL_COMMAND_RESULT).cast();
                }
        ));

        startApp();

        // when
        changeStudentName(student1, "name-1");

        // then
        assertThat(handlerInvocations.get()).isEqualTo(1);
        verifyStudentName(student1, "name-1");
    }

    @Test
    void handlerInterceptorsAreInvokedForEveryCommand() {
        // given
        AtomicInteger handlerInterceptorCounter = new AtomicInteger(0);
        MessageHandlerInterceptor<CommandMessage> countingInterceptor = (message, context, chain) -> {
            handlerInterceptorCounter.incrementAndGet();
            return chain.proceed(message, context);
        };

        registerCommandHandlerInterceptor(countingInterceptor);

        registerCommandHandlers(handlerPhase -> handlerPhase.commandHandler(
                new QualifiedName(ChangeStudentNameCommand.class),
                c -> (command, context) -> {
                    EventAppender eventAppender = EventAppender.forContext(context);
                    ChangeStudentNameCommand payload =
                            command.payloadAs(ChangeStudentNameCommand.class, c.getComponent(Converter.class));
                    StateManager state = context.component(StateManager.class);
                    Student student = state.loadEntity(Student.class, payload.id(), context).join();
                    eventAppender.append(new StudentNameChangedEvent(student.getId(), payload.name()));
                    return MessageStream.just(SUCCESSFUL_COMMAND_RESULT).cast();
                }
        ));

        startApp();

        // when
        changeStudentName(student1, "name-1");
        changeStudentName(student1, "name-2");
        changeStudentName(student1, "name-3");

        // then
        assertThat(handlerInterceptorCounter.get()).isEqualTo(3);
    }

    @Test
    void dispatchAndHandlerInterceptorsBothModifyCommand() {
        // given
        MessageDispatchInterceptor<Message> dispatchInterceptor = new AddMetadataInterceptor<>("dispatch", "value");
        MessageHandlerInterceptor<CommandMessage> handlerInterceptor = new AddMetadataInterceptor<>("handler", "value");

        AtomicInteger handlerInvocations = new AtomicInteger(0);

        registerCommandDispatchInterceptor(dispatchInterceptor);
        registerCommandHandlerInterceptor(handlerInterceptor);

        registerCommandHandlers(handlerPhase -> handlerPhase.commandHandler(
                new QualifiedName(ChangeStudentNameCommand.class),
                c -> (command, context) -> {
                    handlerInvocations.incrementAndGet();

                    // Verify that both interceptors added metadata to the command
                    assertTrue(command.metadata().containsKey("dispatch"),
                               "Expected dispatch interceptor to add metadata to command");
                    assertTrue(command.metadata().containsKey("handler"),
                               "Expected handler interceptor to add metadata to command");

                    EventAppender eventAppender = EventAppender.forContext(context);
                    ChangeStudentNameCommand payload =
                            command.payloadAs(ChangeStudentNameCommand.class, c.getComponent(Converter.class));
                    StateManager state = context.component(StateManager.class);
                    Student student = state.loadEntity(Student.class, payload.id(), context).join();
                    eventAppender.append(new StudentNameChangedEvent(student.getId(), payload.name()));
                    return MessageStream.just(SUCCESSFUL_COMMAND_RESULT).cast();
                }
        ));

        startApp();

        // when
        changeStudentName(student1, "name-1");

        // then
        assertThat(handlerInvocations.get()).isEqualTo(1);
        verifyStudentName(student1, "name-1");
    }

    @Test
    void handlerInterceptorCanModifyCommandResponse() {
        // given
        AtomicInteger interceptorInvocations = new AtomicInteger(0);

        // Inline interceptor implementation that modifies the command response
        MessageHandlerInterceptor<CommandMessage> responseModifyingInterceptor = (message, context, chain) -> {
            interceptorInvocations.incrementAndGet();

            // Proceed with the command handling
            return chain.proceed(message, context)
                        // Modify the response by adding metadata after handler execution
                        .mapMessage(responseMessage -> {
                            // Add metadata to indicate the response was intercepted and modified
                            return responseMessage.andMetadata(Map.of(
                                    "intercepted", "true"
                            ));
                        });
        };

        registerCommandHandlerInterceptor(responseModifyingInterceptor);

        registerCommandHandlers(handlerPhase -> handlerPhase.commandHandler(
                new QualifiedName(ChangeStudentNameCommand.class),
                c -> (command, context) -> {
                    EventAppender eventAppender = EventAppender.forContext(context);
                    ChangeStudentNameCommand payload =
                            command.payloadAs(ChangeStudentNameCommand.class, c.getComponent(Converter.class));
                    StateManager state = context.component(StateManager.class);
                    Student student = state.loadEntity(Student.class, payload.id(), context).join();
                    eventAppender.append(new StudentNameChangedEvent(student.getId(), payload.name()));

                    // Return a result with some metadata
                    return MessageStream.just(
                            new GenericCommandResultMessage(
                                    SUCCESSFUL_COMMAND_RESULT.type(),
                                    "Command handled successfully"
                            )
                    ).cast();
                }
        ));

        startApp();

        // when
        GenericCommandResultMessage result = sendCommand(
                new ChangeStudentNameCommand(student1, "name-1"),
                GenericCommandResultMessage.class
        );

        // then
        assertThat(interceptorInvocations.get()).isEqualTo(1);
        assertTrue(result.metadata().containsKey("intercepted"),
                   "Expected interceptor to add 'intercepted' metadata to response");
        assertEquals("true", result.metadata().get("intercepted"));
        verifyStudentName(student1, "name-1");
    }

    static class SingleModelAnnotatedCommandHandler {

        @CommandHandler
        public void handle(ChangeStudentNameCommand command,
                           @InjectEntity Student student,
                           EventAppender eventAppender) {
            // Change name through event
            eventAppender.append(new StudentNameChangedEvent(student.getId(), command.name()));
            // Entity through magic of repository automatically updated
            assertEquals(student.getName(), command.name());
        }
    }

    /**
     * Test interceptor that adds metadata to command messages.
     */
    private record AddMetadataInterceptor<M extends Message>(String key, String value)
            implements MessageHandlerInterceptor<M>, MessageDispatchInterceptor<M> {

        @Override
        public MessageStream<?> interceptOnDispatch(M message,
                                                    ProcessingContext context,
                                                    MessageDispatchInterceptorChain<M> interceptorChain) {
            @SuppressWarnings("unchecked")
            var intercepted = (M) message.andMetadata(Map.of(key, value));
            return interceptorChain.proceed(intercepted, context);
        }

        @Override
        public MessageStream<?> interceptOnHandle(M message,
                                                  ProcessingContext context,
                                                  MessageHandlerInterceptorChain<M> interceptorChain) {
            @SuppressWarnings("unchecked")
            var intercepted = (M) message.andMetadata(Map.of(key, value));
            return interceptorChain.proceed(intercepted, context);
        }
    }

    private void verifyStudentName(String id, String name) {
        UnitOfWork uow = unitOfWorkFactory.create();
        uow.executeWithResult(context -> context.component(StateManager.class)
                                                .repository(Student.class, String.class)
                                                .load(id, context)
                                                .thenAccept(student -> assertEquals(name,
                                                                                    student.entity().getName())))
           .join();
    }
}