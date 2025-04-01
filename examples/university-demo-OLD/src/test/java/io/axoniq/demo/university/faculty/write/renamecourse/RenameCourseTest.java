package io.axoniq.demo.university.faculty.write.renamecourse;

import io.axoniq.demo.university.UniversityAxonApplication;
import io.axoniq.demo.university.faculty.events.CourseRenamed;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.*;

import java.util.UUID;

class RenameCourseTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        var application = new UniversityAxonApplication();
        fixture = AxonTestFixture.with(application.configurer());
    }

    @Test
    void givenNotExistingCourse_WhenRenameCourse_ThenSuccess() {
        var courseId = UUID.randomUUID().toString();
        var courseName = "Event Sourcing in Practice";

        fixture.given()
               .when()
               .command(new RenameCourse(courseId, courseName))
               .then()
               .events(new CourseRenamed(courseId, courseName));
    }
}
