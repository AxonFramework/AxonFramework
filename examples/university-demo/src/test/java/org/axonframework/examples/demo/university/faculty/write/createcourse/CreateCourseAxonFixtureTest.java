package org.axonframework.examples.demo.university.faculty.write.createcourse;

import org.axonframework.examples.demo.university.faculty.FacultyAxonTestFixture.CreateCourseConfigurationFixture;
import org.axonframework.examples.demo.university.faculty.Ids;
import org.axonframework.examples.demo.university.faculty.events.CourseCreated;
import org.axonframework.examples.demo.university.shared.ids.CourseId;
import org.axonframework.test.extension.AxonTestFixtureExtension;
import org.axonframework.test.extension.ProvidedAxonTestFixture;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(AxonTestFixtureExtension.class)
@ProvidedAxonTestFixture(CreateCourseConfigurationFixture.class)
class CreateCourseAxonFixtureTest {

    @Test
    void givenNotExistingCourse_WhenCreateCourse_ThenSuccess(final AxonTestFixture fixture) {
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
    void givenCourseCreated_WhenCreateCourse_ThenSuccess_NoEvents(final AxonTestFixture fixture) {
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
    void givenCourseWithSameName_WhenCreateCourse_ThenFail(final AxonTestFixture fixture) {
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
