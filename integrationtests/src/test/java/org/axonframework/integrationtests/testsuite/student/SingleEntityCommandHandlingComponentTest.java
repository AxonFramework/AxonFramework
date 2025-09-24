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


import org.axonframework.commandhandling.annotations.CommandHandler;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.integrationtests.testsuite.student.commands.ChangeStudentNameCommand;
import org.axonframework.integrationtests.testsuite.student.events.StudentNameChangedEvent;
import org.axonframework.integrationtests.testsuite.student.state.Student;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.annotations.InjectEntity;
import org.axonframework.serialization.Converter;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests whether stateful command handling components can process commands with a single entity.
 *
 * @author Mitchell Herrijgers
 */
class SingleEntityCommandHandlingComponentTest extends AbstractCommandHandlingStudentTestSuite {

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

        changeStudentName("my-studentId-1", "name-1");
        verifyStudentName("my-studentId-1", "name-1");
        changeStudentName("my-studentId-1", "name-2");
        verifyStudentName("my-studentId-1", "name-2");
        changeStudentName("my-studentId-1", "name-3");
        verifyStudentName("my-studentId-1", "name-3");
        changeStudentName("my-studentId-1", "name-4");
        verifyStudentName("my-studentId-1", "name-4");

        changeStudentName("my-studentId-2", "name-5");
        verifyStudentName("my-studentId-1", "name-4");
        verifyStudentName("my-studentId-2", "name-5");
    }

    @Test
    void canHandleCommandThatTargetsOneModelViaStateManagerParameter() {
        registerCommandHandlers(handlerPhase -> handlerPhase.annotatedCommandHandlingComponent(
                c -> new SingleModelAnnotatedCommandHandler()
        ));
        startApp();

        changeStudentName("my-studentId-1", "name-1");
        verifyStudentName("my-studentId-1", "name-1");

        changeStudentName("my-studentId-1", "name-2");
        verifyStudentName("my-studentId-1", "name-2");
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