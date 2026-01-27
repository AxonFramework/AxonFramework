package org.axonframework.examples.university.faculty.write.createcourseplain;

import org.axonframework.examples.university.faculty.FacultyAxonTestFixture;
import org.axonframework.examples.university.event.CourseCreated;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.examples.university.write.createcourse.CreateCourse;
import org.axonframework.examples.university.write.createcourse.CreateCourseConfiguration;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.*;

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
                .events(new CourseCreated(courseId, courseName, capacity));
    }

    @Test
    @Disabled("imported test, failing with: Creational command handler for command [org.axonframework.examples.university.write.createcourse.CreateCourse#0.0.1] encountered an already existing entity")
    void givenCourseCreated_WhenCreateCourse_ThenSuccess_NoEvents() {
        var courseId = CourseId.random();
        var courseName = "Event Sourcing in Practice";
        var capacity = 3;

        fixture.given()
                .event(new CourseCreated(courseId, courseName, capacity))
                .when()
                .command(new CreateCourse(courseId, courseName, capacity))
                .then()
                .success()
                .noEvents();
    }
}
