/*
 * Copyright (c) 2010-2026. Axon Framework
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
import org.axonframework.common.configuration.ApplicationConfigurer;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.conversion.Converter;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.integrationtests.testsuite.student.commands.EnrollStudentToCourseCommand;
import org.axonframework.integrationtests.testsuite.student.commands.UnenrollStudentFromCourseCommand;
import org.axonframework.integrationtests.testsuite.student.events.StudentEnrolledEvent;
import org.axonframework.integrationtests.testsuite.student.events.StudentUnenrolledEvent;
import org.axonframework.integrationtests.testsuite.student.state.Course;
import org.axonframework.messaging.commandhandling.CommandExecutionException;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.Tag;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.StateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test demonstrating separate SourceCriteria and AppendCriteria for Course enrollment.
 * <p>
 * This test uses Dynamic Consistency Boundaries (DCB) where:
 * <ul>
 *   <li><b>SourceCriteria</b>: Load both {@link StudentEnrolledEvent} AND {@link StudentUnenrolledEvent}
 *       to calculate current student count</li>
 *   <li><b>AppendCriteria</b>: Only check for conflicts on {@link StudentEnrolledEvent} (enrollments)
 *       to prevent concurrent enrollments from overflowing the limit</li>
 * </ul>
 * <p>
 * This allows concurrent unenrollments while preventing concurrent enrollments from exceeding the course capacity.
 *
 * @author Mateusz Nowak
 */
class CourseEnrollmentsAppendCriteriaNotEqualSourcingCriteriaIT extends AbstractCommandHandlingStudentIT {

    private static final int MAX_COURSE_CAPACITY = 3;

    private final String student1 = createId("student-1");
    private final String student2 = createId("student-2");
    private final String student3 = createId("student-3");
    private final String student4 = createId("student-4");
    private final String course1 = createId("course-1");

    @Override
    @BeforeEach
    protected void prepareModule() {
        // Only configure Course entity with separate source/append criteria
        // We don't need the Student entity for this test - we're only testing
        // Course enrollment limit enforcement
        courseEntity = EventSourcedEntityModule
                .declarative(String.class, Course.class)
                .messagingModel((c, b) -> b
                        .entityEvolver(courseEvolverWithUnenroll(c))
                        .build())
                .entityFactory(c -> EventSourcedEntityFactory.fromIdentifier(Course::new))
                .criteriaResolvers(
                        // SOURCE: Load both enrollments AND unenrollments to know current count
                        c -> (courseId, ctx) -> EventCriteria.havingTags(new Tag("Course", courseId))
                                .andBeingOneOfTypes(
                                        StudentEnrolledEvent.class.getName(),
                                        StudentUnenrolledEvent.class.getName()
                                ),
                        // APPEND: Only check for concurrent enrollments (not unenrollments)
                        c -> (courseId, ctx) -> EventCriteria.havingTags(new Tag("Course", courseId))
                                .andBeingOneOfTypes(StudentEnrolledEvent.class.getName())
                )
                .build();

        // Keep studentEntity null - we don't register it for this test
        studentEntity = null;
    }

    @Override
    protected ApplicationConfigurer createConfigurer() {
        // Only register courseEntity - we don't need studentEntity for this test
        var configurer = EventSourcingConfigurer.create()
                                                .componentRegistry(cr -> cr.registerModule(courseEntity));
        return testSuiteConfigurer(configurer);
    }

    @BeforeEach
    void registerCommandHandlers() {
        registerCommandHandlers(handlerPhase -> {
            // Enroll command handler
            handlerPhase.commandHandler(
                    new QualifiedName(EnrollStudentToCourseCommand.class),
                    c -> (command, context) -> {
                        EventAppender eventAppender = EventAppender.forContext(context);
                        EnrollStudentToCourseCommand payload =
                                command.payloadAs(EnrollStudentToCourseCommand.class, c.getComponent(Converter.class));
                        StateManager state = context.component(StateManager.class);
                        Course course = state.loadEntity(Course.class, payload.courseId(), context).join();

                        if (course.getStudentsEnrolled().size() >= MAX_COURSE_CAPACITY) {
                            throw new IllegalArgumentException("Course is full. Maximum capacity is " + MAX_COURSE_CAPACITY);
                        }

                        eventAppender.append(new StudentEnrolledEvent(payload.studentId(), payload.courseId()));
                        return MessageStream.just(SUCCESSFUL_COMMAND_RESULT).cast();
                    }
            );

            // Unenroll command handler
            handlerPhase.commandHandler(
                    new QualifiedName(UnenrollStudentFromCourseCommand.class),
                    c -> (command, context) -> {
                        EventAppender eventAppender = EventAppender.forContext(context);
                        UnenrollStudentFromCourseCommand payload =
                                command.payloadAs(UnenrollStudentFromCourseCommand.class, c.getComponent(Converter.class));
                        StateManager state = context.component(StateManager.class);
                        Course course = state.loadEntity(Course.class, payload.courseId(), context).join();

                        if (!course.getStudentsEnrolled().contains(payload.studentId())) {
                            throw new IllegalArgumentException("Student is not enrolled in this course");
                        }

                        eventAppender.append(new StudentUnenrolledEvent(payload.studentId(), payload.courseId()));
                        return MessageStream.just(SUCCESSFUL_COMMAND_RESULT).cast();
                    }
            );
        });
    }

    /**
     * Returns an {@link EntityEvolver} for {@link Course} that handles both enrollment and unenrollment events.
     */
    private EntityEvolver<Course> courseEvolverWithUnenroll(@Nonnull Configuration config) {
        return (course, event, context) -> {
            Converter converter = config.getComponent(Converter.class);
            if (event.type().name().equals(StudentEnrolledEvent.class.getName())) {
                StudentEnrolledEvent convert = converter.convert(event.payload(), StudentEnrolledEvent.class);
                Objects.requireNonNull(convert, "The converted payload must not be null.");
                course.handle(convert);
            } else if (event.type().name().equals(StudentUnenrolledEvent.class.getName())) {
                StudentUnenrolledEvent convert = converter.convert(event.payload(), StudentUnenrolledEvent.class);
                Objects.requireNonNull(convert, "The converted payload must not be null.");
                course.handle(convert);
            }
            return course;
        };
    }

    protected void unenrollStudentFromCourse(String studentId, String courseId) {
        sendCommand(new UnenrollStudentFromCourseCommand(studentId, courseId));
    }

    protected void verifyCourseHasStudents(String courseId, int expectedCount) {
        Course course = unitOfWorkFactory.create()
                                         .executeWithResult(context -> context.component(StateManager.class)
                                                                              .loadEntity(Course.class, courseId, context))
                                         .join();
        assertThat(course.getStudentsEnrolled()).hasSize(expectedCount);
    }

    @Nested
    class BasicEnrollmentAndUnenrollment {

        @Test
        void enrollmentAddsStudentToCourse() {
            // given
            startApp();

            // when
            enrollStudentToCourse(student1, course1);

            // then
            verifyCourseHasStudents(course1, 1);
        }

        @Test
        void unenrollmentRemovesStudentFromCourse() {
            // given
            startApp();
            enrollStudentToCourse(student1, course1);

            // when
            unenrollStudentFromCourse(student1, course1);

            // then
            verifyCourseHasStudents(course1, 0);
        }

        @Test
        void multipleEnrollmentsAndUnenrollmentsWorkCorrectly() {
            // given
            startApp();

            // when
            enrollStudentToCourse(student1, course1);
            enrollStudentToCourse(student2, course1);
            verifyCourseHasStudents(course1, 2);

            unenrollStudentFromCourse(student1, course1);
            verifyCourseHasStudents(course1, 1);

            enrollStudentToCourse(student3, course1);

            // then
            verifyCourseHasStudents(course1, 2);
        }
    }

    @Nested
    class MaxCapacityEnforcement {

        @Test
        void cannotEnrollMoreThanMaxCapacity() {
            // given
            startApp();

            // Fill course to max capacity
            enrollStudentToCourse(student1, course1);
            enrollStudentToCourse(student2, course1);
            enrollStudentToCourse(student3, course1);

            // when / then
            assertThatThrownBy(() -> enrollStudentToCourse(student4, course1))
                    .isInstanceOf(CommandExecutionException.class)
                    .hasMessageContaining("Course is full");
        }

        @Test
        void unenrollmentAllowsAnotherStudentToEnroll() {
            // given
            startApp();

            // Fill course to max capacity
            enrollStudentToCourse(student1, course1);
            enrollStudentToCourse(student2, course1);
            enrollStudentToCourse(student3, course1);

            // when
            unenrollStudentFromCourse(student1, course1);

            // then - fourth student can now enroll
            enrollStudentToCourse(student4, course1);
            verifyCourseHasStudents(course1, 3);
        }
    }

    @Nested
    class UnenrollmentValidation {

        @Test
        void cannotUnenrollStudentNotEnrolledInCourse() {
            // given
            startApp();

            // when / then
            assertThatThrownBy(() -> unenrollStudentFromCourse(student1, course1))
                    .isInstanceOf(CommandExecutionException.class)
                    .hasMessageContaining("Student is not enrolled in this course");
        }
    }

    /**
     * Tests verifying the Dynamic Consistency Boundary (DCB) behavior where:
     * - Source criteria includes both enrollment and unenrollment events (for accurate state)
     * - Append criteria only includes enrollment events (to avoid conflicts from unenrollments)
     * <p>
     * This enables concurrent unenrollments without causing conflicts for enrollments.
     */
    @Nested
    class DynamicConsistencyBoundaryBehavior {

        /**
         * Tests that an unenrollment event stored directly (simulating concurrent modification)
         * is correctly included in the source criteria for state calculation,
         * but does NOT cause a conflict when appending an enrollment.
         * <p>
         * Scenario:
         * <ol>
         *   <li>Enroll 2 students via commands</li>
         *   <li>Directly store an unenrollment event (simulating concurrent unenrollment by another process)</li>
         *   <li>Enroll a 3rd student - should succeed because:
         *       <ul>
         *         <li>Source criteria loads all events → count = 1 (correct state)</li>
         *         <li>Append criteria only checks enrollments → unenrollment doesn't cause conflict</li>
         *       </ul>
         *   </li>
         * </ol>
         */
        @Test
        void enrollmentSucceedsAfterConcurrentUnenrollmentBecauseAppendCriteriaOnlyChecksEnrollments() {
            // given
            startApp();

            // Enroll 2 students via commands
            enrollStudentToCourse(student1, course1);
            enrollStudentToCourse(student2, course1);
            verifyCourseHasStudents(course1, 2);

            // Simulate concurrent unenrollment by directly storing the event
            // This bypasses the command handler, as if another process did it
            storeEvent(StudentUnenrolledEvent.class, new StudentUnenrolledEvent(student1, course1));

            // when - enroll a third student
            // With DCB:
            // - Source criteria loads: Enrolled(s1), Enrolled(s2), Unenrolled(s1) → count = 1
            // - Append criteria only looks at Enrolled events, so Unenrolled(s1) doesn't cause conflict
            enrollStudentToCourse(student3, course1);

            // then - course should have 2 students (s2 and s3)
            verifyCourseHasStudents(course1, 2);
        }

        /**
         * Tests that source criteria correctly loads unenrollment events to calculate accurate state.
         * Without proper source criteria, the count would be wrong.
         */
        @Test
        void sourceCriteriaCorrectlyIncludesUnenrollmentEventsForStateCalculation() {
            // given
            startApp();

            // Enroll 3 students - course is full
            enrollStudentToCourse(student1, course1);
            enrollStudentToCourse(student2, course1);
            enrollStudentToCourse(student3, course1);
            verifyCourseHasStudents(course1, 3);

            // Directly store unenrollment (simulating concurrent modification)
            storeEvent(StudentUnenrolledEvent.class, new StudentUnenrolledEvent(student1, course1));

            // when - try to enroll 4th student
            // If source criteria didn't include unenrollments, count would be 3 and this would fail
            // But with proper source criteria, count is 2, so enrollment succeeds
            enrollStudentToCourse(student4, course1);

            // then
            verifyCourseHasStudents(course1, 3); // s2, s3, s4
        }

        /**
         * Tests that multiple unenrollments are correctly handled by the source criteria,
         * allowing new enrollments up to the freed capacity.
         */
        @Test
        void multipleUnenrollmentsAreCorrectlyReflectedInState() {
            // given
            startApp();

            // Enroll 3 students - course is full
            enrollStudentToCourse(student1, course1);
            enrollStudentToCourse(student2, course1);
            enrollStudentToCourse(student3, course1);

            // Directly store 2 unenrollments
            storeEvent(StudentUnenrolledEvent.class, new StudentUnenrolledEvent(student1, course1));
            storeEvent(StudentUnenrolledEvent.class, new StudentUnenrolledEvent(student2, course1));

            // when - verify state is correctly calculated
            verifyCourseHasStudents(course1, 1); // only s3 remains

            // then - can enroll 2 more students
            enrollStudentToCourse(student4, course1);
            String student5 = createId("student-5");
            enrollStudentToCourse(student5, course1);
            verifyCourseHasStudents(course1, 3);
        }
    }
}
