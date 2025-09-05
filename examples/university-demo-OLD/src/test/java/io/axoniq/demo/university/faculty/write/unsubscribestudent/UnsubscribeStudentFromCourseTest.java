package io.axoniq.demo.university.faculty.write.unsubscribestudent;

import io.axoniq.demo.university.UniversityApplicationTest;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.faculty.events.StudentUnsubscribedFromCourse;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.junit.jupiter.api.Test;

class UnsubscribeStudentFromCourseTest extends UniversityApplicationTest {

    @Override
    protected EventSourcingConfigurer overrideConfigurer(EventSourcingConfigurer configurer) {
        return UnsubscribeStudentConfiguration.configure(configurer);
    }

    @Test
    void successfulUnsubscribe() {
        // given
        var studentId = StudentId.random();
        var courseId = CourseId.random();

        eventsOccurred(
                new StudentEnrolledInFaculty(studentId, "Novak", "Djokovic"),
                new CourseCreated(courseId, "Tennis", 1),
                new StudentSubscribedToCourse(studentId, courseId)
        );

        // when
        executeCommand(new UnsubscribeStudentFromCourse(studentId, courseId));

        // then
        assertEvents(new StudentUnsubscribedFromCourse(studentId, courseId));
    }

    @Test
    void unsubscribeWithoutStudentBeingSubscribed() {
        // given
        var courseId = CourseId.random();
        var studentId = StudentId.random();

        // when
        executeCommand(new UnsubscribeStudentFromCourse(studentId, courseId));

        // then
        assertNoEvents();
    }

    @Test
    void unsubscribeAfterUnsubscription() {
        // given
        var courseId = CourseId.random();
        var studentId = StudentId.random();

        eventsOccurred(
                new StudentSubscribedToCourse(studentId, courseId),
                new StudentUnsubscribedFromCourse(studentId, courseId)
        );

        // when
        executeCommand(new UnsubscribeStudentFromCourse(studentId, courseId));

        // then
        assertNoEvents();
    }
}