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


import org.axonframework.integrationtests.testsuite.student.commands.EnrollStudentToCourseCommand;
import org.axonframework.integrationtests.testsuite.student.events.StudentEnrolledEvent;
import org.axonframework.integrationtests.testsuite.student.state.Course;
import org.axonframework.integrationtests.testsuite.student.state.Student;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests whether stateful command handling components can handle commands that target multiple entities at the same time.
 */
class MultiEntityCommandHandlingComponentTest extends AbstractStudentTestSuite {

    @Test
    void canCombineStatesInLambdaCommandHandlerViaStateManagerParameter() {
        var component = StatefulCommandHandlingComponent
                .create("MyStatefulCommandHandlingComponent", registry)
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
}