package io.axoniq.demo.university.faculty.write.renamecourse;

import io.axoniq.demo.university.faculty.FacultyAxonTestFixture;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.CourseRenamed;
import io.axoniq.demo.university.shared.ids.CourseId;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

class RenameCourseAxonFixtureTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        fixture = FacultyAxonTestFixture.slice(RenameCourseConfiguration::configure);
    }

    @AfterEach
    void afterEach() {
        fixture.stop();
    }

    @Test
    void givenNotExistingCourse_WhenRenameCourse_ThenException() {
        var courseId = CourseId.random();

        fixture.given()
                .noPriorActivity()
                .when()
                .command(new RenameCourse(courseId, "Event Sourcing in Practice"))
                .then()
                .noEvents()
                .exceptionSatisfies(thrown -> assertThat(thrown)
                        .hasMessageContaining("Course with given id does not exist")
                );
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
