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
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.integrationtests.testsuite.student.commands.AssignMentorCommand;
import org.axonframework.integrationtests.testsuite.student.commands.EnrollStudentToCourseCommand;
import org.axonframework.integrationtests.testsuite.student.events.MentorAssignedToStudentEvent;
import org.axonframework.integrationtests.testsuite.student.events.StudentEnrolledEvent;
import org.axonframework.integrationtests.testsuite.student.state.Course;
import org.axonframework.integrationtests.testsuite.student.state.Student;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.annotation.InjectEntity;
import org.axonframework.modelling.command.EntityIdResolver;
import org.axonframework.modelling.repository.ManagedEntity;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.*;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests whether stateful command handling components can process commands that target multiple entities at the same
 * time.
 *
 * @author Mitchell Herrijgers
 */
class MultiEntityCommandHandlingComponentTest extends AbstractStudentTestSuite {

    @Test
    void canCombineModelsInAnnotatedCommandHandlerViaStateManagerParameter() {
        registerCommandHandlers(handlerPhase -> handlerPhase.annotatedCommandHandlingComponent(
                c -> new MultiModelAnnotatedCommandHandler()
        ));
        startApp();

        enrollStudentToCourse("my-studentId-1", "my-courseId-1");

        verifyStudentEnrolledInCourse("my-studentId-1", "my-courseId-1");
    }

    @Test
    void canCombineStatesInLambdaCommandHandlerViaStateManagerParameter() {
        registerCommandHandlers(handlerPhase -> handlerPhase.commandHandler(
                new QualifiedName(EnrollStudentToCourseCommand.class),
                c -> (command, state, context) -> {
                    EventAppender eventAppender = EventAppender.forContext(context, c);
                    EnrollStudentToCourseCommand payload = (EnrollStudentToCourseCommand) command.getPayload();
                    Student student = state.loadEntity(Student.class, payload.studentId(), context).join();
                    Course course = state.loadEntity(Course.class, payload.courseId(), context).join();

                    if (student.getCoursesEnrolled().size() > 2) {
                        throw new IllegalArgumentException("Student already enrolled in 3 courses");
                    }

                    if (course.getStudentsEnrolled().size() > 2) {
                        throw new IllegalArgumentException("Course already has 3 students");
                    }
                    eventAppender.append(new StudentEnrolledEvent(payload.studentId(), payload.courseId()));
                    return MessageStream.just(SUCCESSFUL_COMMAND_RESULT).cast();
                }
        ));
        startApp();

        // First student
        enrollStudentToCourse("my-studentId-2", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-2", "my-courseId-1");

        // Second student
        enrollStudentToCourse("my-studentId-3", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-3", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-2", "my-courseId-1");

        // Third and last possible student
        enrollStudentToCourse("my-studentId-4", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-4", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-3", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-2", "my-courseId-1");

        // Fourth can still enroll for other course
        enrollStudentToCourse("my-studentId-4", "my-courseId-2");
        verifyStudentEnrolledInCourse("my-studentId-4", "my-courseId-2");

        // But five can not enroll for the first course
        var exception = assertThrows(CommandExecutionException.class,
                                     () -> enrollStudentToCourse("my-studentId-5", "my-courseId-1"));
        Throwable commandExecutionExceptionCause = exception.getCause();
        assertInstanceOf(ExecutionException.class, commandExecutionExceptionCause);
        Throwable executionExceptionCause = commandExecutionExceptionCause.getCause();
        assertInstanceOf(IllegalArgumentException.class, executionExceptionCause);
        assertTrue(executionExceptionCause.getMessage().contains("Course already has 3 students"));
    }

    @Test
    void canHandleCommandThatTargetsMultipleOfTheSameModelInSameAnnotatedCommandHandler() {
        registerCommandHandlers(handlerPhase -> handlerPhase.annotatedCommandHandlingComponent(
                c -> new MultiModelAnnotatedCommandHandler()
        ));
        startApp();

        // Can assign mentor to mentee
        sendCommand(new AssignMentorCommand("my-studentId-1", "my-studentId-2"));

        // But not a second time
        var exception = assertThrows(CommandExecutionException.class,
                                     () -> sendCommand(
                                             new AssignMentorCommand("my-studentId-1", "my-studentId-3")
                                     ));
        Throwable commandExecutionExceptionCause = exception.getCause();
        assertInstanceOf(ExecutionException.class, commandExecutionExceptionCause);
        Throwable executionExceptionCause = commandExecutionExceptionCause.getCause();
        assertInstanceOf(IllegalArgumentException.class, executionExceptionCause);
        assertTrue(executionExceptionCause.getMessage().contains("Mentor already assigned to a mentee"));
    }

    private static class MultiModelAnnotatedCommandHandler {

        @SuppressWarnings("unused")
        @CommandHandler
        public void handle(EnrollStudentToCourseCommand command,
                           @InjectEntity(idProperty = "studentId") Student student,
                           @InjectEntity(idProperty = "courseId") Course course,
                           EventAppender eventAppender,
                           ProcessingContext context) {
            if (student.getCoursesEnrolled().size() > 2) {
                throw new IllegalArgumentException("Student already enrolled in 3 courses");
            }

            if (course.getStudentsEnrolled().size() > 2) {
                throw new IllegalArgumentException("Course already has 3 students");
            }

            eventAppender.append( new StudentEnrolledEvent(command.studentId(), command.courseId()));

            assertTrue(student.getCoursesEnrolled().contains(command.courseId()));
            assertTrue(course.getStudentsEnrolled().contains(command.studentId()));
        }

        @SuppressWarnings("unused")
        @CommandHandler
        public void handle(AssignMentorCommand command,
                           @InjectEntity(idResolver = MentorIdResolver.class) Student mentor,
                           @InjectEntity(idProperty = "menteeId") ManagedEntity<?, Student> mentee,
                           EventAppender eventAppender,
                           ProcessingContext context) {
            if (mentor.getMenteeId() != null) {
                throw new IllegalArgumentException("Mentor already assigned to a mentee");
            }
            if (mentee.entity().getMentorId() != null) {
                throw new IllegalArgumentException("Mentee already has a mentor");
            }

            eventAppender.append(
                    new MentorAssignedToStudentEvent(mentor.getId(), mentee.entity().getId())
            );
        }

        public static class MentorIdResolver implements EntityIdResolver<String> {

            @Override
            @Nonnull
            public String resolve(@Nonnull Message<?> command, @Nonnull ProcessingContext context) {
                //noinspection unused
                if (command.getPayload() instanceof AssignMentorCommand(String studentId, String mentorId)) {
                    return studentId;
                }
                throw new IllegalArgumentException("Can not resolve mentor id from command");
            }
        }
    }
}