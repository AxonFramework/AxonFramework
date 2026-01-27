package org.axonframework.examples.university.faculty.write.unsubscribestudent;

import org.axonframework.examples.university.UniversityApplicationTest;
import org.axonframework.examples.university.event.CourseCreated;
import org.axonframework.examples.university.event.StudentEnrolledInFaculty;
import org.axonframework.examples.university.event.StudentSubscribedToCourse;
import org.axonframework.examples.university.event.StudentUnsubscribedFromCourse;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.examples.university.write.unsubscribestudent.UnsubscribeStudentConfiguration;
import org.axonframework.examples.university.write.unsubscribestudent.UnsubscribeStudentFromCourse;
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
        var studentId = "student-" + java.util.UUID.randomUUID().toString();
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
        var studentId = "student-" + java.util.UUID.randomUUID().toString();

        // when
        executeCommand(new UnsubscribeStudentFromCourse(studentId, courseId));

        // then
        assertNoEvents();
    }

    @Test
    void unsubscribeAfterUnsubscription() {
        // given
        var courseId = CourseId.random();
        var studentId = "student-" + java.util.UUID.randomUUID().toString();

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