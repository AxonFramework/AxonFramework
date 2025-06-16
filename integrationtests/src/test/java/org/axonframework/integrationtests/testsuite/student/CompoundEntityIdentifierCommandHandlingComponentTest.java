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


import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.Tag;
import org.axonframework.integrationtests.testsuite.student.commands.AssignMentorCommand;
import org.axonframework.integrationtests.testsuite.student.common.StudentMentorModelIdentifier;
import org.axonframework.integrationtests.testsuite.student.events.MentorAssignedToStudentEvent;
import org.axonframework.integrationtests.testsuite.student.state.StudentMentorAssignment;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.SimpleEntityEvolvingComponent;
import org.axonframework.modelling.annotation.InjectEntity;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;
import org.axonframework.modelling.entity.EntityModel;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the injection of a compound entity based on a compound id that loads events of two tags.
 *
 * @author Mitchell Herrijgers
 */
class CompoundEntityIdentifierCommandHandlingComponentTest extends AbstractStudentTestSuite {

    @Override
    protected void registerAdditionalEntities(StatefulCommandHandlingModule.EntityPhase entityConfigurer) {
        EventSourcedEntityModule<StudentMentorModelIdentifier, StudentMentorAssignment> mentorAssignmentSlice =
                EventSourcedEntityModule
                        .declarative(StudentMentorModelIdentifier.class, StudentMentorAssignment.class)
                        .entityModel(c -> EntityModel
                                .forEntityType(StudentMentorAssignment.class)
                                .entityEvolver(
                                        new SimpleEntityEvolvingComponent<>(
                                                Map.of(
                                                        new QualifiedName(MentorAssignedToStudentEvent.class),
                                                        (entity, event, context) -> {
                                                            entity.handle((MentorAssignedToStudentEvent) event.getPayload());
                                                            return entity;
                                                        }
                                                )
                                        )
                                )
                                .build()
                        )
                        .entityFactory(c -> EventSourcedEntityFactory.fromIdentifier(StudentMentorAssignment::new))
                        .criteriaResolver(c -> (id, ctx) -> EventCriteria.either(
                                EventCriteria.havingTags(new Tag("Student", id.menteeId())),
                                EventCriteria.havingTags(new Tag("Student", id.mentorId()))
                                             .andBeingOneOfTypes(MentorAssignedToStudentEvent.class.getName())
                        ))
                        .withoutCommandHandling();

        entityConfigurer.entity(mentorAssignmentSlice);
    }

    @Test
    void canHandleCommandThatTargetsMultipleModelsViaInjectionOfCompoundModel() {
        registerCommandHandlers(handlerPhase -> handlerPhase.annotatedCommandHandlingComponent(
                c -> new CompoundModelAnnotatedCommandHandler()
        ));
        startApp();

        verifyMentorLogicForComponent();
    }

    @Test
    void canHandleCommandThatTargetsMultipleModelsViaStatefulCommandHandler() {
        registerCommandHandlers(handlerPhase -> handlerPhase.commandHandler(
                new QualifiedName(AssignMentorCommand.class),
                c -> (command, state, context) -> {
                    EventAppender eventAppender = EventAppender.forContext(context, c);
                    AssignMentorCommand payload = (AssignMentorCommand) command.getPayload();
                    StudentMentorAssignment assignment = state.loadEntity(
                            StudentMentorAssignment.class, payload.modelIdentifier(), context
                    ).join();

                    if (assignment.isMentorHasMentee()) {
                        throw new IllegalArgumentException("Mentor already assigned to a mentee");
                    }
                    if (assignment.isMenteeHasMentor()) {
                        throw new IllegalArgumentException("Mentee already has a mentor");
                    }
                    eventAppender.append(new MentorAssignedToStudentEvent(payload.mentorId(),
                                                                          payload.menteeId()));
                    return MessageStream.just(SUCCESSFUL_COMMAND_RESULT).cast();
                }
        ));
        startApp();

        // Can assign mentor to mentee
        verifyMentorLogicForComponent();
    }

    private void verifyMentorLogicForComponent() {
        // Can assign mentor to mentee
        sendCommand(new AssignMentorCommand("my-studentId-1", "my-studentId-2"));

        // But not a second time
        var exception = assertThrows(CommandExecutionException.class,
                                     () -> sendCommand(new AssignMentorCommand("my-studentId-1", "my-studentId-3")));
        Throwable commandExecutionExceptionCause = exception.getCause();
        assertInstanceOf(ExecutionException.class, commandExecutionExceptionCause);
        Throwable executionExceptionCause = commandExecutionExceptionCause.getCause();
        assertInstanceOf(IllegalArgumentException.class, executionExceptionCause);
        assertTrue(executionExceptionCause.getMessage().contains("Mentee already has a mentor"));

        // And a third student can't become the mentee of the second, because the second is already a mentor
        var exceptionTwo = assertThrows(CommandExecutionException.class,
                                        () -> sendCommand(new AssignMentorCommand("my-studentId-3", "my-studentId-2")));
        Throwable commandExecutionExceptionCauseTwo = exceptionTwo.getCause();
        assertInstanceOf(ExecutionException.class, commandExecutionExceptionCauseTwo);
        Throwable executionExceptionCauseTwo = commandExecutionExceptionCauseTwo.getCause();
        assertInstanceOf(IllegalArgumentException.class, executionExceptionCauseTwo);
        assertTrue(executionExceptionCauseTwo.getMessage().contains("Mentor already assigned to a mentee"));

        // But the mentee can become a mentor for a third student
        sendCommand(new AssignMentorCommand("my-studentId-2", "my-studentId-3"));

        // And that third can become a mentor for the first
        sendCommand(new AssignMentorCommand("my-studentId-3", "my-studentId-1"));
    }

    class CompoundModelAnnotatedCommandHandler {

        @CommandHandler
        public void handle(AssignMentorCommand command,
                           @InjectEntity StudentMentorAssignment assignment,
                           EventAppender appender
        ) {
            if (assignment.isMentorHasMentee()) {
                throw new IllegalArgumentException("Mentor already assigned to a mentee");
            }
            if (assignment.isMenteeHasMentor()) {
                throw new IllegalArgumentException("Mentee already has a mentor");
            }

            appender.append(new MentorAssignedToStudentEvent(command.mentorId(), command.menteeId()));
        }
    }
}