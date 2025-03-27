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


import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.annotation.AnnotatedCommandHandlingComponent;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.integrationtests.testsuite.student.commands.AssignMentorCommand;
import org.axonframework.integrationtests.testsuite.student.commands.EnrollStudentToCourseCommand;
import org.axonframework.integrationtests.testsuite.student.events.MentorAssignedToStudentEvent;
import org.axonframework.integrationtests.testsuite.student.events.StudentEnrolledEvent;
import org.axonframework.integrationtests.testsuite.student.state.Course;
import org.axonframework.integrationtests.testsuite.student.state.Student;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.command.EntityIdResolver;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.axonframework.modelling.command.annotation.InjectEntity;
import org.axonframework.modelling.repository.ManagedEntity;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests whether stateful command handling components can handle commands that target multiple entities at the same time.
 */
class MultiEntityCommandHandlingComponentTest extends AbstractStudentTestSuite {


    @Test
    void canCombineModelsInAnnotatedCommandHandlerViaStateManagerParameter() {
        MultiModelAnnotatedCommandHandler handler = new MultiModelAnnotatedCommandHandler();

        var component = StatefulCommandHandlingComponent
                .create("InjectedStateHandler", stateManager)
                .subscribe(new AnnotatedCommandHandlingComponent<>(
                        handler,
                        getParameterResolverFactory()));


        enrollStudentToCourse(component, "my-studentId-1", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-1", "my-courseId-1");
    }

    @Test
    void canCombineStatesInLambdaCommandHandlerViaStateManagerParameter() {
        var component = StatefulCommandHandlingComponent
                .create("MyStatefulCommandHandlingComponent", stateManager)
                .subscribe(
                        new QualifiedName(EnrollStudentToCourseCommand.class),
                        (command, state, context) -> {
                            EnrollStudentToCourseCommand payload = (EnrollStudentToCourseCommand) command.getPayload();
                            Student student = state.loadEntity(Student.class, payload.studentId(), context).join();
                            Course course = state.loadEntity(Course.class, payload.courseId(), context).join();

                            if (student.getCoursesEnrolled().size() > 2) {
                                throw new IllegalArgumentException(
                                        "Student already enrolled in 3 courses");
                            }

                            if (course.getStudentsEnrolled().size() > 2) {
                                throw new IllegalArgumentException("Course already has 3 students");
                            }
                            appendEvent(context,
                                        new StudentEnrolledEvent(payload.studentId(), payload.courseId()));
                            return MessageStream.empty().cast();
                        });

        // First student
        enrollStudentToCourse(component, "my-studentId-2", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-2", "my-courseId-1");

        // Second student
        enrollStudentToCourse(component, "my-studentId-3", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-3", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-2", "my-courseId-1");

        // Third and last possible student
        enrollStudentToCourse(component, "my-studentId-4", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-4", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-3", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-2", "my-courseId-1");

        // Fourth can still enroll for other course
        enrollStudentToCourse(component, "my-studentId-4", "my-courseId-2");
        verifyStudentEnrolledInCourse("my-studentId-4", "my-courseId-2");

        // But five can not enroll for the first course
        var exception = assertThrows(CompletionException.class,
                                     () -> enrollStudentToCourse(component, "my-studentId-5", "my-courseId-1"
                                     ));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Course already has 3 students"));
    }

    @Test
    void canHandleCommandThatTargetsMultipleOfTheSameModelInSameAnnotatedCommandHandler() {

        MultiModelAnnotatedCommandHandler handler = new MultiModelAnnotatedCommandHandler();
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
                                                       new AssignMentorCommand("my-studentId-1", "my-studentId-3")
                                     ));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Mentor already assigned to a mentee"));
    }


    class MultiModelAnnotatedCommandHandler {

        @CommandHandler
        public void handle(EnrollStudentToCourseCommand command,
                           StateManager stateManager,
                           EventSink eventSink,
                           ProcessingContext context
        ) {
            Student student = stateManager.loadEntity(Student.class, command.studentId(), context).join();

            if (student.getCoursesEnrolled().size() > 2) {
                throw new IllegalArgumentException("Student already enrolled in 3 courses");
            }

            // Lazy-loading, so only load course if the student is able to enroll
            Course course = stateManager.loadEntity(Course.class, command.courseId(), context).join();
            if (course.getStudentsEnrolled().size() > 2) {
                throw new IllegalArgumentException("Course already has 3 students");
            }

            eventSink.publish(context, new GenericEventMessage<>(
                    new MessageType(StudentEnrolledEvent.class),
                    new StudentEnrolledEvent(command.studentId(), command.courseId())
            ));

            assertTrue(student.getCoursesEnrolled().contains(command.courseId()));
            assertTrue(course.getStudentsEnrolled().contains(command.studentId()));
        }


        @CommandHandler
        public void handle(AssignMentorCommand command,
                           @InjectEntity(idResolver = MentorIdResolver.class) Student mentor,
                           @InjectEntity(idProperty = "menteeId") ManagedEntity<?, Student> mentee,
                           EventSink eventSink,
                           ProcessingContext context
        ) {
            if (mentor.getMenteeId() != null) {
                throw new IllegalArgumentException("Mentor already assigned to a mentee");
            }
            if (mentee.entity().getMentorId() != null) {
                throw new IllegalArgumentException("Mentee already has a mentor");
            }

            eventSink.publish(context, new GenericEventMessage<>(
                    new MessageType(MentorAssignedToStudentEvent.class),
                    new MentorAssignedToStudentEvent(mentor.getId(), mentee.entity().getId())
            ));
        }

        public static class MentorIdResolver implements EntityIdResolver<String> {

            @Override
            @NotNull
            public String resolve(@Nonnull Message<?> command, @Nonnull ProcessingContext context) {
                if (command.getPayload() instanceof AssignMentorCommand(String studentId, String mentorId)) {
                    return studentId;
                }
                throw new IllegalArgumentException("Can not resolve mentor id from command");
            }
        }
    }
}