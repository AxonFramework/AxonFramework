package org.axonframework.examples.demo.university.faculty.write.createcourse;

import org.axonframework.examples.demo.university.faculty.FacultyAxonTestFixture;
import org.axonframework.examples.demo.university.faculty.Ids;
import org.axonframework.examples.demo.university.faculty.events.CourseCreated;
import org.axonframework.examples.demo.university.shared.ids.CourseId;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateCourseAxonFixtureTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        fixture = FacultyAxonTestFixture.slice(CreateCourseConfiguration::configure);
    }

    @AfterEach
    void afterEach() {
        fixture.stop();
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
                .events(new CourseCreated(Ids.FACULTY_ID, courseId, courseName, capacity));
    }

    @Test
    void givenCourseCreated_WhenCreateCourse_ThenSuccess_NoEvents() {
        var courseId = CourseId.random();
        var courseName = "Event Sourcing in Practice";
        var capacity = 3;

        fixture.given()
                .event(new CourseCreated(Ids.FACULTY_ID, courseId, courseName, capacity))
                .when()
                .command(new CreateCourse(courseId, courseName, capacity))
                .then()
                .success()
                .noEvents();
    }

    @Test
    void givenCourseWithSameName_WhenCreateCourse_ThenFail() {
        var existingCourseId = CourseId.random();
        var newCourseId = CourseId.random();
        var courseName = "Event Sourcing in Practice";
        var capacity = 3;

        fixture.given()
                .event(new CourseCreated(Ids.FACULTY_ID, existingCourseId, courseName, capacity))
                .when()
                .command(new CreateCourse(newCourseId, courseName, capacity))
                .then()
                .exceptionSatisfies(
                        exception -> assertThat(exception)
                                .hasMessageContaining("Course with name 'Event Sourcing in Practice' already exists")
                );
    }
}
