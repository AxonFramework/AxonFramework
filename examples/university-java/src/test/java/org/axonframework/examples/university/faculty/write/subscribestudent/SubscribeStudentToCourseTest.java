package org.axonframework.examples.university.faculty.write.subscribestudent;

import org.axonframework.examples.university.UniversityApplicationTest;
import org.axonframework.examples.university.event.CourseCreated;
import org.axonframework.examples.university.event.StudentEnrolledInFaculty;
import org.axonframework.examples.university.event.StudentSubscribedToCourse;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.examples.university.write.subscribestudent.SubscribeStudentConfiguration;
import org.axonframework.examples.university.write.subscribestudent.SubscribeStudentToCourse;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscribeStudentToCourseTest extends UniversityApplicationTest {

    @Override
    protected EventSourcingConfigurer overrideConfigurer(EventSourcingConfigurer configurer) {
        return SubscribeStudentConfiguration.configure(configurer);
    }

    @Test
    void successfulSubscription() {
        // given
        var courseId = CourseId.random();
        var studentId = "student-" + java.util.UUID.randomUUID().toString();

        eventsOccurred(
                new StudentEnrolledInFaculty(studentId, "Mateusz", "Nowak"),
                new CourseCreated(courseId, "Axon Framework 5: Be a PRO", 2)
        );

        // when
        executeCommand(new SubscribeStudentToCourse(studentId, courseId));

        // then
        assertEvents(new StudentSubscribedToCourse(studentId, courseId));
    }

    @Test
    void studentAlreadySubscribed() {
        // given
        var courseId = CourseId.random();
        var studentId = "student-" + java.util.UUID.randomUUID().toString();

        eventsOccurred(
                new StudentEnrolledInFaculty(studentId, "Allard", "Buijze"),
                new CourseCreated(courseId, "Axon Framework 5: Be a PRO", 2),
                new StudentSubscribedToCourse(studentId, courseId)
        );

        // when-then
        assertThatThrownBy(() -> executeCommand(
                new SubscribeStudentToCourse(studentId, courseId)
        )).hasMessageContaining("Student already subscribed to this course");
        assertNoEvents();
    }

    @Test
    void courseFullyBooked() {
        // given
        var courseId = CourseId.random();
        var student1Id = "student-" + java.util.UUID.randomUUID().toString();
        var student2Id = "student-" + java.util.UUID.randomUUID().toString();
        var student3Id = "student-" + java.util.UUID.randomUUID().toString();

        eventsOccurred(
                new StudentEnrolledInFaculty(student1Id, "Mateusz", "Nowak"),
                new StudentEnrolledInFaculty(student2Id, "Steven", "van Beelen"),
                new StudentEnrolledInFaculty(student3Id, "Mitchell", "Herrijgers"),
                new CourseCreated(courseId, "Event Sourcing Masterclass", 2),
                new StudentSubscribedToCourse(student1Id, courseId),
                new StudentSubscribedToCourse(student2Id, courseId)
        );

        // when-then
        assertThatThrownBy(() -> executeCommand(
                new SubscribeStudentToCourse(student3Id, courseId)
        )).hasMessageContaining("Course is fully booked");
        assertNoEvents();
    }

    @RepeatedTest(10)
    void studentSubscribedToTooManyCourses() {
        // given
        var studentId = "student-" + java.util.UUID.randomUUID().toString();
        var course1Id = CourseId.random();
        var course2Id = CourseId.random();
        var course3Id = CourseId.random();
        var targetCourseId = CourseId.random();

        eventsOccurred(
                new StudentEnrolledInFaculty(studentId, "Milan", "Savic"),
                new CourseCreated(targetCourseId, "Programming", 10),
                new CourseCreated(course1Id, "Course 1", 10),
                new CourseCreated(course2Id, "Course 2", 10),
                new CourseCreated(course3Id, "Course 3", 10),
                new StudentSubscribedToCourse(studentId, course1Id),
                new StudentSubscribedToCourse(studentId, course2Id),
                new StudentSubscribedToCourse(studentId, course3Id)
        );

        // when-then
        assertThatThrownBy(() -> executeCommand(
                new SubscribeStudentToCourse(studentId, targetCourseId)
        )).hasMessageContaining("Student subscribed to too many courses");
        assertNoEvents();
    }
}