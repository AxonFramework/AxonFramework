package io.axoniq.demo.university.faculty.write.renamecourse;

import io.axoniq.demo.university.UniversityAxonApplication;
import io.axoniq.demo.university.faculty.events.CourseCreated;
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

        fixture.given()
               .noPriorActivity()
               .when()
               .command(new RenameCourse(courseId, "Event Sourcing in Practice"))
               .then()
               .exception(RuntimeException.class)
               .noEvents();
    }

    @Test
    void givenCourseCreated_WhenRenameCourse_ThenSuccess() {
        var courseId = CourseId.random();

        fixture.given()
               .event(new CourseCreated(courseId.raw(), "Event Sourcing in Practice", 10))
               .when()
               .command(new RenameCourse(courseId, "Event Sourcing in Theory"))
               .then()
               .success()
               .events(new CourseRenamed(courseId.raw(), "Event Sourcing in Theory"));
    }
}
