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
import org.axonframework.commandhandling.annotation.AnnotatedCommandHandlingComponent;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.AnnotationBasedEventStateApplier;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityBuilder;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.integrationtests.testsuite.student.commands.AssignMentorCommand;
import org.axonframework.integrationtests.testsuite.student.common.StudentMentorModelIdentifier;
import org.axonframework.integrationtests.testsuite.student.events.MentorAssignedToStudentEvent;
import org.axonframework.integrationtests.testsuite.student.state.StudentMentorAssignment;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.annotation.InjectEntity;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;
import org.junit.jupiter.api.*;

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
        EventSourcedEntityBuilder<StudentMentorModelIdentifier, StudentMentorAssignment> mentorAssignmentSlice =
                EventSourcedEntityBuilder.entity(StudentMentorModelIdentifier.class, StudentMentorAssignment.class)
                                         .entityFactory(c -> StudentMentorAssignment::new)
                                         .criteriaResolver(c -> id -> EventCriteria.either(
                                                 EventCriteria.match()
                                                              .eventsOfTypes(MentorAssignedToStudentEvent.class.getName())
                                                              .withTags(new Tag("Student", id.menteeId())),
                                                 EventCriteria.match()
                                                              .eventsOfTypes(MentorAssignedToStudentEvent.class.getName())
                                                              .withTags(new Tag("Student", id.mentorId()))
                                         ))
                                         .eventStateApplier(
                                                 c -> new AnnotationBasedEventStateApplier<>(StudentMentorAssignment.class)
                                         );

        entityConfigurer.entity(mentorAssignmentSlice);
    }

    @Test
    void canHandleCommandThatTargetsMultipleModelsViaInjectionOfCompoundModel() {
        registerCommandHandlers(handlerPhase -> handlerPhase.commandHandlingComponent(
                c -> new AnnotatedCommandHandlingComponent<>(
                        new CompoundModelAnnotatedCommandHandler(),
                        parameterResolverFactory(c)
                )
        ));
        startApp();

        verifyMentorLogicForComponent();
    }

    @Test
    void canHandleCommandThatTargetsMultipleModelsViaStatefulCommandHandler() {
        registerCommandHandlers(handlerPhase -> handlerPhase.commandHandler(
                new QualifiedName(AssignMentorCommand.class),
                (command, state, context) -> {
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
                    appendEvent(context,
                                new MentorAssignedToStudentEvent(payload.mentorId(), payload.menteeId()));
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

    static class CompoundModelAnnotatedCommandHandler {

        @CommandHandler
        public void handle(AssignMentorCommand command,
                           @InjectEntity StudentMentorAssignment assignment,
                           EventSink eventSink,
                           ProcessingContext context) {
            if (assignment.isMentorHasMentee()) {
                throw new IllegalArgumentException("Mentor already assigned to a mentee");
            }
            if (assignment.isMenteeHasMentor()) {
                throw new IllegalArgumentException("Mentee already has a mentor");
            }

            eventSink.publish(context,
                              new GenericEventMessage<>(
                                      new MessageType(MentorAssignedToStudentEvent.class),
                                      new MentorAssignedToStudentEvent(command.mentorId(), command.menteeId())
                              ));
        }
    }
}