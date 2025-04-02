package io.axoniq.demo.university.faculty.write.createcourse;

import io.axoniq.demo.university.UniversityAxonApplication;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.write.CourseId;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.*;

class CreateCourseTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        var application = new UniversityAxonApplication();
        fixture = AxonTestFixture.with(application.configurer());
    }

    @Test
    void givenNotExistingCourse_WhenCreateCourse_ThenSuccess() {
        var courseId = CourseId.random();
        var courseName = "Event Sourcing in Practice";
        var capacity = 3;

        fixture.given()
               .when()
               .command(new CreateCourse(courseId, courseName, capacity))
               .then()
               .success()
               .events(new CourseCreated(courseId.raw(), courseName, capacity));
    }
}
