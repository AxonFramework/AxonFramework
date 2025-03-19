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
import org.axonframework.integrationtests.testsuite.student.commands.AssignMentorCommand;
import org.axonframework.integrationtests.testsuite.student.events.MentorAssignedToStudentEvent;
import org.axonframework.integrationtests.testsuite.student.state.StudentMentorAssignmentModel;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.axonframework.modelling.command.annotation.InjectEntity;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the injection of a compound entity based on a compound id that loads events of two tags. Currently is disabled,
 * as two requirements are not met:
 * <ol>
 *     <li>EventStore does not support multiple tags in a single query</li>
 *     <li>EventCriteria does not support OR on tags</li>
 * </ol>
 * In time, I expect this test to work, and for now it serves as an example.
 * NOTE: Using manual, temporary code edits this test WORKED.
 */
class CompoundEntityIdentifierCommandHandlingComponentTest extends AbstractStudentTestSuite {

    @Test
    @Disabled
    void canHandleCommandThatTargetsCompoundEntityUsingInjection() {

        CompoundModelAnnotatedCommandHandler handler = new CompoundModelAnnotatedCommandHandler();
        var component = StatefulCommandHandlingComponent
                .create("InjectedStateHandler", stateManager)
                .subscribe(new AnnotatedCommandHandlingComponent<>(
                        handler,
                        getParameterResolverFactory()));

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
    }

    class CompoundModelAnnotatedCommandHandler {

        @CommandHandler
        public void handle(AssignMentorCommand command,
                           @InjectEntity StudentMentorAssignmentModel model,
                           EventSink eventSink,
                           ProcessingContext context
        ) {
            if (model.isMentorHasMentee()) {
                throw new IllegalArgumentException("Mentor already assigned to a mentee");
            }
            if (model.isMenteeHasMentor()) {
                throw new IllegalArgumentException("Mentee already has a mentor");
            }

            eventSink.publish(context,
                              DEFAULT_CONTEXT,
                              new GenericEventMessage<>(
                                      new MessageType(MentorAssignedToStudentEvent.class),
                                      new MentorAssignedToStudentEvent(command.mentorId(), command.menteeId())
                              ));
        }
    }
}