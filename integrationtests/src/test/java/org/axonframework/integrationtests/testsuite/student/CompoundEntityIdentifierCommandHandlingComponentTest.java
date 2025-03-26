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


import org.axonframework.commandhandling.annotation.AnnotatedCommandHandlingComponent;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.AnnotationBasedEventStateApplier;
import org.axonframework.eventsourcing.AsyncEventSourcingRepository;
import org.axonframework.eventsourcing.EventStateApplier;
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
import org.axonframework.modelling.SimpleStateManager;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.axonframework.modelling.command.annotation.InjectEntity;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the injection of a compound entity based on a compound id that loads events of two tags.
 */
class CompoundEntityIdentifierCommandHandlingComponentTest extends AbstractStudentTestSuite {

    protected EventStateApplier<StudentMentorAssignment> studentMentorModelApplier = new AnnotationBasedEventStateApplier<>(
            StudentMentorAssignment.class);


    protected AsyncEventSourcingRepository<StudentMentorModelIdentifier, StudentMentorAssignment> mentorAssignmentRepository = new AsyncEventSourcingRepository<>(
            StudentMentorModelIdentifier.class,
            StudentMentorAssignment.class,
            eventStore,
            id ->
                    EventCriteria.either(
                            EventCriteria.match()
                                         .eventsOfTypes(MentorAssignedToStudentEvent.class.getName())
                                         .withTags(new Tag("Student", id.menteeId())),
                            EventCriteria.match()
                                         .eventsOfTypes(MentorAssignedToStudentEvent.class.getName())
                                         .withTags(new Tag("Student", id.mentorId()))
                    ),
            studentMentorModelApplier,
            StudentMentorAssignment::new
    );

    @Override
    protected void registerAdditionalModels(SimpleStateManager.Builder builder) {
        builder.register(mentorAssignmentRepository);
    }

    @Test
    void canHandleCommandThatTargetsMultipleModelsViaInjectionOfCompoundModel() {

        CompoundModelAnnotatedCommandHandler handler = new CompoundModelAnnotatedCommandHandler();
        var component = StatefulCommandHandlingComponent
                .create("InjectedStateHandler", stateManager)
                .subscribe(new AnnotatedCommandHandlingComponent<>(
                        handler,
                        getParameterResolverFactory()));

        verifyMentorLogicForComponent(component);
    }

    private void verifyMentorLogicForComponent(StatefulCommandHandlingComponent component) {
        // Can assign mentor to mentee
        sendCommand(component, new AssignMentorCommand("my-studentId-1", "my-studentId-2"));

        // But not a second time
        var exception = assertThrows(CompletionException.class,
                                     () -> sendCommand(component,
                                                       new AssignMentorCommand("my-studentId-1",
                                                                               "my-studentId-3")
                                     ));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Mentee already has a mentor"));

        // And a third student can't become the mentee of the second, because the second is already a mentor
        var exception2 = assertThrows(CompletionException.class,
                                      () -> sendCommand(component,
                                                        new AssignMentorCommand("my-studentId-3",
                                                                                "my-studentId-2")
                                      ));
        assertInstanceOf(IllegalArgumentException.class, exception2.getCause());
        assertTrue(exception2.getCause().getMessage().contains("Mentor already assigned to a mentee"));

        // But the mentee can become a mentor for a third student
        sendCommand(component, new AssignMentorCommand("my-studentId-2", "my-studentId-3"));

        // And that third can become a mentor for the first
        sendCommand(component, new AssignMentorCommand("my-studentId-3", "my-studentId-1"));
    }


    @Test
    void canHandleCommandThatTargetsMultipleModelsViaStatefulCommandHandler() {
        var component = StatefulCommandHandlingComponent
                .create("InjectedStateHandler", stateManager)
                .subscribe(
                        new QualifiedName(AssignMentorCommand.class),
                        (command, state, context) -> {
                            AssignMentorCommand payload = (AssignMentorCommand) command.getPayload();
                            StudentMentorAssignment assignment = stateManager.loadEntity(
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
                            return MessageStream.empty().cast();
                        });

        // Can assign mentor to mentee
        verifyMentorLogicForComponent(component);
    }

    class CompoundModelAnnotatedCommandHandler {

        @CommandHandler
        public void handle(AssignMentorCommand command,
                           @InjectEntity StudentMentorAssignment assignment,
                           EventSink eventSink,
                           ProcessingContext context
        ) {
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