package io.axoniq.demo.university.faculty.write.renamecourse;

import io.axoniq.demo.university.UniversityAxonApplication;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.CourseRenamed;
import io.axoniq.demo.university.shared.ids.CourseId;
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
    void givenNotExistingCourse_WhenRenameCourse_ThenException() {
        var courseId = CourseId.random();

        fixture.given()
               .noPriorActivity()
               .when()
               .command(new RenameCourse(courseId, "Event Sourcing in Practice"))
               .then()
               .exception(RuntimeException.class, "Course with given id does not exist")
               .noEvents();
    }

    @Test
    void givenCourseCreated_WhenRenameCourse_ThenSuccess() {
        var courseId = CourseId.random();

        fixture.given()
               .event(new CourseCreated(courseId, "Event Sourcing in Practice", 42))
               .when()
               .command(new RenameCourse(courseId, "Event Sourcing in Theory"))
               .then()
               .success()
               .events(new CourseRenamed(courseId, "Event Sourcing in Theory"));
    }

    @Test
    void givenCourseCreated_WhenRenameCourseToTheSameName_ThenSuccess_NoEvents() {
        var courseId = CourseId.random();

        fixture.given()
               .event(new CourseCreated(courseId, "Event Sourcing in Practice", 42))
               .when()
               .command(new RenameCourse(courseId, "Event Sourcing in Practice"))
               .then()
               .success()
               .noEvents();
    }

    @Test
    void givenCourseCreatedAndRenamed_WhenRenameCourse_ThenSuccess() {
        var courseId = CourseId.random();

        fixture.given()
               .event(new CourseCreated(courseId, "Event Sourcing in Practice", 42))
               .event(new CourseRenamed(courseId, "Event Sourcing in Theory"))
               .when()
               .command(new RenameCourse(courseId, "Theoretical Practice of Event Sourcing"))
               .then()
               .success()
               .events(new CourseRenamed(courseId, "Theoretical Practice of Event Sourcing"));
    }
}
