package io.axoniq.demo.university.faculty.write.unsubscribestudent;

import io.axoniq.demo.university.UniversityAxonApplication;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentEnrolledFaculty;
import io.axoniq.demo.university.faculty.events.StudentSubscribed;
import io.axoniq.demo.university.faculty.events.StudentUnsubscribed;
import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.*;

class UnsubscribeStudentFromCourseTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        var application = new UniversityAxonApplication();
        fixture = AxonTestFixture.with(application.configurer());
    }

    @Test
    void successfulUnsubscribe() {
        var studentId = StudentId.random();
        var courseId = CourseId.random();

        fixture.given()
               .event(new StudentEnrolledFaculty(studentId.raw(), "Novak", "Djokovic"))
               .event(new CourseCreated(courseId.raw(), "Tennis", 1))
               .event(new StudentSubscribed(studentId.raw(), courseId.raw()))
               .when()
               .command(new UnsubscribeStudentFromCourse(studentId, courseId))
               .then()
               .events(new StudentUnsubscribed(studentId.raw(), courseId.raw()));
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
               .event(new StudentSubscribed(studentId.raw(), courseId.raw()))
               .event(new StudentUnsubscribed(studentId.raw(), courseId.raw()))
               .when()
               .command(new UnsubscribeStudentFromCourse(studentId, courseId))
               .then()
               .noEvents();
    }
}
