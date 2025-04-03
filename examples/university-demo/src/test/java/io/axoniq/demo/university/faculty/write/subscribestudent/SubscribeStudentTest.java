package io.axoniq.demo.university.faculty.write.subscribestudent;

import io.axoniq.demo.university.UniversityAxonApplication;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentEnrolledFaculty;
import io.axoniq.demo.university.faculty.events.StudentSubscribed;
import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.*;

class SubscribeStudentTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        var application = new UniversityAxonApplication();
        fixture = AxonTestFixture.with(application.configurer());
    }

    @Test
    void successfulSubscription() {
        var courseId = CourseId.random();
        var studentId = StudentId.random();

        fixture.given()
               .event(new StudentEnrolledFaculty(studentId.raw(), "Jan", "Kowalski"))
               .event(new CourseCreated(courseId.raw(), "History", 10))
               .when()
               .command(new SubscribeStudent(studentId, courseId))
               .then()
               .events(new StudentSubscribed(studentId.raw(), courseId.raw()));
    }
}
