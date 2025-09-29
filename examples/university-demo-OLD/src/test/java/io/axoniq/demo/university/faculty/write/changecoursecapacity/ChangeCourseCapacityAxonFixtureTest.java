package io.axoniq.demo.university.faculty.write.changecoursecapacity;

import io.axoniq.demo.university.faculty.FacultyAxonTestFixture;
import io.axoniq.demo.university.faculty.events.CourseCapacityChanged;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.CourseRenamed;
import io.axoniq.demo.university.shared.ids.CourseId;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeCourseCapacityAxonFixtureTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        fixture = FacultyAxonTestFixture.slice(ChangeCourseCapacityConfiguration::configure);
    }

    @AfterEach
    void afterEach() {
        fixture.stop();
    }

    @Test
    void givenNotExistingCourse_WhenChangeCapacity_ThenException() {
        var courseId = CourseId.random();

        fixture.given()
                .noPriorActivity()
                .when()
                .command(new ChangeCourseCapacity(courseId, 5))
                .then()
                .exceptionSatisfies(ex -> assertThat(ex) // CommandExecutionException(AxonServerRemoteCommandHandlingException) in AxonServer, but not in the InMemory? Where handle that?
                        .hasMessageContaining("Course with given id does not exist")
                );
    }

    @Test
    void givenCourseCreated_WhenChangeCapacity_ThenSuccess() {
        var courseId = CourseId.random();

        fixture.given()
                .event(new CourseCreated(courseId, "Event Sourcing in Practice", 42))
                .when()
                .command(new ChangeCourseCapacity(courseId, 7))
                .then()
                .success()
                .events(new CourseCapacityChanged(courseId, 7));
    }

    @Test
    void givenCourseCreated_WhenChangeCapacityToTheSameName_ThenSuccess_NoEvents() {
        var courseId = CourseId.random();

        fixture.given()
                .event(new CourseCreated(courseId, "Event Sourcing in Practice", 42))
                .when()
                .command(new ChangeCourseCapacity(courseId, 42))
                .then()
                .success()
                .noEvents();
    }

    @Test
    void givenCourseCreatedAndRenamed_WhenChangeCapacity_ThenSuccess() {
        var courseId = CourseId.random();

        fixture.given()
                .event(new CourseCreated(courseId, "Event Sourcing in Practice", 42))
                .event(new CourseRenamed(courseId, "Event Sourcing in Theory"))
                .when()
                .command(new ChangeCourseCapacity(courseId, 7))
                .then()
                .success()
                .events(new CourseCapacityChanged(courseId, 7));
    }

}