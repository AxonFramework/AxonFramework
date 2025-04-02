package io.axoniq.demo.university.faculty.write.renamecourse;

import io.axoniq.demo.university.UniversityAxonApplication;
import io.axoniq.demo.university.faculty.events.CourseRenamed;
import io.axoniq.demo.university.faculty.write.CourseId;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.*;

class RenameCourseTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        var application = new UniversityAxonApplication();
        fixture = AxonTestFixture.with(application.configurer());
    }

    @Test
    void givenNotExistingCourse_WhenRenameCourse_ThenSuccess() {
        var courseId = CourseId.random();
        var courseName = "Event Sourcing in Practice";

        fixture.given()
               .when()
               .command(new RenameCourse(courseId, courseName))
               .then()
               .events(new CourseRenamed(courseId.raw(), courseName));
    }
}
