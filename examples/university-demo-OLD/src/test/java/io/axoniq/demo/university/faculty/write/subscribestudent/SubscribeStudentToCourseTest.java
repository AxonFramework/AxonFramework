package io.axoniq.demo.university.faculty.write.subscribestudent;

import io.axoniq.demo.university.UniversityApplicationTest;
import io.axoniq.demo.university.faculty.Ids;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
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
        var studentId = StudentId.random();

        eventsOccurred(
                new StudentEnrolledInFaculty(Ids.FACULTY_ID, studentId, "Mateusz", "Nowak"),
                new CourseCreated(Ids.FACULTY_ID, courseId, "Axon Framework 5: Be a PRO", 2)
        );

        // when
        executeCommand(new SubscribeStudentToCourse(studentId, courseId));

        // then
        assertEvents(new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId, courseId));
    }

    @Test
    void studentAlreadySubscribed() {
        // given
        var courseId = CourseId.random();
        var studentId = StudentId.random();

        eventsOccurred(
                new StudentEnrolledInFaculty(Ids.FACULTY_ID, studentId, "Allard", "Buijze"),
                new CourseCreated(Ids.FACULTY_ID, courseId, "Axon Framework 5: Be a PRO", 2),
                new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId, courseId)
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
        var student1Id = StudentId.random();
        var student2Id = StudentId.random();
        var student3Id = StudentId.random();

        eventsOccurred(
                new StudentEnrolledInFaculty(Ids.FACULTY_ID, student1Id, "Mateusz", "Nowak"),
                new StudentEnrolledInFaculty(Ids.FACULTY_ID, student2Id, "Steven", "van Beelen"),
                new StudentEnrolledInFaculty(Ids.FACULTY_ID, student3Id, "Mitchell", "Herrijgers"),
                new CourseCreated(Ids.FACULTY_ID, courseId, "Event Sourcing Masterclass", 2),
                new StudentSubscribedToCourse(Ids.FACULTY_ID, student1Id, courseId),
                new StudentSubscribedToCourse(Ids.FACULTY_ID, student2Id, courseId)
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
        var studentId = StudentId.random();
        var course1Id = CourseId.random();
        var course2Id = CourseId.random();
        var course3Id = CourseId.random();
        var targetCourseId = CourseId.random();

        eventsOccurred(
                new StudentEnrolledInFaculty(Ids.FACULTY_ID, studentId, "Milan", "Savic"),
                new CourseCreated(Ids.FACULTY_ID, targetCourseId, "Programming", 10),
                new CourseCreated(Ids.FACULTY_ID, course1Id, "Course 1", 10),
                new CourseCreated(Ids.FACULTY_ID, course2Id, "Course 2", 10),
                new CourseCreated(Ids.FACULTY_ID, course3Id, "Course 3", 10),
                new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId, course1Id),
                new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId, course2Id),
                new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId, course3Id)
        );

        // when-then
        assertThatThrownBy(() -> executeCommand(
                new SubscribeStudentToCourse(studentId, targetCourseId)
        )).hasMessageContaining("Student subscribed to too many courses");
        assertNoEvents();
    }
}