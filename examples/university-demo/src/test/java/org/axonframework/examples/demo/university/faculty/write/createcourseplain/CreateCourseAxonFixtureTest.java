package org.axonframework.examples.demo.university.faculty.write.createcourseplain;

import org.axonframework.examples.demo.university.faculty.Ids;
import org.axonframework.examples.demo.university.faculty.events.CourseCreated;
import org.axonframework.examples.demo.university.shared.ids.CourseId;
import org.axonframework.test.extension.AxonTestFixtureExtension;
import org.axonframework.test.extension.AxonTestFixtureProvider;
import org.axonframework.test.extension.ProvidedAxonTestFixture;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;

import static org.axonframework.examples.demo.university.faculty.FacultyAxonTestFixture.sliceProvider;

@ExtendWith(AxonTestFixtureExtension.class)
class CreateCourseAxonFixtureTest {

    @ProvidedAxonTestFixture
    private final AxonTestFixtureProvider fixtureProvider = sliceProvider(CreateCoursePlainConfiguration::configure);

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
}
