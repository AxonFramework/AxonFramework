package org.axonframework.examples.university.faculty.write.unsubscribestudent;

import org.axonframework.examples.university.faculty.FacultyAxonTestFixture;
import org.axonframework.examples.university.event.CourseCreated;
import org.axonframework.examples.university.event.StudentEnrolledInFaculty;
import org.axonframework.examples.university.event.StudentSubscribedToCourse;
import org.axonframework.examples.university.event.StudentUnsubscribedFromCourse;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.examples.university.write.unsubscribestudent.UnsubscribeStudentConfiguration;
import org.axonframework.examples.university.write.unsubscribestudent.UnsubscribeStudentFromCourse;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnsubscribeStudentFromCourseAxonFixtureTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        fixture = FacultyAxonTestFixture.slice(UnsubscribeStudentConfiguration::configure);
    }

    @AfterEach
    void afterEach() {
        fixture.stop();
    }

    @Test
    void successfulUnsubscribe() {
        var studentId = "student-" + java.util.UUID.randomUUID().toString();
        var courseId = CourseId.random();

        fixture.given()
                .event(new StudentEnrolledInFaculty(studentId, "Novak", "Djokovic"))
                .event(new CourseCreated(courseId, "Tennis", 1))
                .event(new StudentSubscribedToCourse(studentId, courseId))
                .when()
                .command(new UnsubscribeStudentFromCourse(studentId, courseId))
                .then()
                .events(new StudentUnsubscribedFromCourse(studentId, courseId));
    }

    @Test
    void unsubscribeWithoutStudentBeingSubscribed() {
        var courseId = CourseId.random();
        var studentId = "student-" + java.util.UUID.randomUUID().toString();

        fixture.when()
                .command(new UnsubscribeStudentFromCourse(studentId, courseId))
                .then()
                .noEvents();
    }

    @Test
    void unsubscribeAfterUnsubscription() {
        var courseId = CourseId.random();
        var studentId = "student-" + java.util.UUID.randomUUID().toString();

        fixture.given()
                .event(new StudentSubscribedToCourse(studentId, courseId))
                .event(new StudentUnsubscribedFromCourse(studentId, courseId))
                .when()
                .command(new UnsubscribeStudentFromCourse(studentId, courseId))
                .then()
                .noEvents();
    }
}
