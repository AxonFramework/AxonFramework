package io.axoniq.demo.university.faculty.write.unsubscribestudent;

import io.axoniq.demo.university.faculty.FacultyAxonTestFixture;
import io.axoniq.demo.university.faculty.Ids;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.faculty.events.StudentUnsubscribedFromCourse;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
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
        var studentId = StudentId.random();
        var courseId = CourseId.random();

        fixture.given()
                .event(new StudentEnrolledInFaculty(Ids.FACULTY_ID, studentId, "Novak", "Djokovic"))
                .event(new CourseCreated(Ids.FACULTY_ID, courseId, "Tennis", 1))
                .event(new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId, courseId))
                .when()
                .command(new UnsubscribeStudentFromCourse(studentId, courseId))
                .then()
                .events(new StudentUnsubscribedFromCourse(Ids.FACULTY_ID, studentId, courseId));
    }

    @Test
    void unsubscribeWithoutStudentBeingSubscribed() {
        var courseId = CourseId.random();
        var studentId = StudentId.random();

        fixture.when()
                .command(new UnsubscribeStudentFromCourse(studentId, courseId))
                .then()
                .noEvents();
    }

    @Test
    void unsubscribeAfterUnsubscription() {
        var courseId = CourseId.random();
        var studentId = StudentId.random();

        fixture.given()
                .event(new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId, courseId))
                .event(new StudentUnsubscribedFromCourse(Ids.FACULTY_ID, studentId, courseId))
                .when()
                .command(new UnsubscribeStudentFromCourse(studentId, courseId))
                .then()
                .noEvents();
    }
}
