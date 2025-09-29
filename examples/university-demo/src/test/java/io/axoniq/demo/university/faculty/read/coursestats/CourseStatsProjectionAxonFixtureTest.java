package io.axoniq.demo.university.faculty.read.coursestats;

import io.axoniq.demo.university.faculty.FacultyAxonTestFixture;
import io.axoniq.demo.university.faculty.events.*;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.configuration.Configuration;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CourseStatsProjectionAxonFixtureTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        fixture = FacultyAxonTestFixture.slice(CourseStatsConfiguration::configure);
    }

    @AfterEach
    void afterEach() {
        fixture.stop();
    }

    @Test
    void givenNotExistingCourse_WhenGetById_ThenNotFound() {
        var courseId = CourseId.random();

        fixture.when()
                .nothing()
                .then()
                .expect(cfg -> assertReadModelNotFound(cfg, courseId));
    }

    @Test
    void givenCourseCreated_WhenGetById_ThenFoundCourseWithInitialCapacity() {
        var courseId = CourseId.random();
        CoursesStatsReadModel expectedReadModel = new CoursesStatsReadModel(
                courseId,
                "Event Sourcing in Practice",
                42,
                0
        );

        fixture.given()
                .events(new CourseCreated(courseId, "Event Sourcing in Practice", 42))
                .then()
                .await(r -> r.expect(cfg -> assertReadModel(cfg, expectedReadModel)));
    }

    @Test
    void givenCourseCreated_WhenCourseRenamed_ThenReadModelUpdatedWithNewName() {
        var courseId = CourseId.random();
        var originalName = "Event Sourcing in Practice";
        var newName = "Advanced Event Sourcing";

        CoursesStatsReadModel expectedReadModel = new CoursesStatsReadModel(
                courseId,
                newName,
                42,
                0
        );

        fixture.given()
                .events(new CourseCreated(courseId, originalName, 42), new CourseRenamed(courseId, newName))
                .then()
                .await(r -> r.expect(cfg -> assertReadModel(cfg, expectedReadModel)));
    }

    @Test
    void givenCourseCreated_WhenCourseCapacityChanged_ThenReadModelUpdatedWithNewCapacity() {
        var courseId = CourseId.random();
        var originalCapacity = 42;
        var newCapacity = 100;

        CoursesStatsReadModel expectedReadModel = new CoursesStatsReadModel(
                courseId,
                "Event Sourcing in Practice",
                newCapacity,
                0
        );

        fixture.given()
                .events(new CourseCreated(courseId, "Event Sourcing in Practice", originalCapacity),
                        new CourseCapacityChanged(courseId, newCapacity))
                .then()
                .await(r -> r.expect(cfg -> assertReadModel(cfg, expectedReadModel)));
    }

    @Test
    void givenCourseCreated_WhenStudentSubscribedToCourse_ThenReadModelUpdatedWithIncreasedSubscribedStudents() {
        var courseId = CourseId.random();
        var studentId = StudentId.random();

        CoursesStatsReadModel expectedReadModel = new CoursesStatsReadModel(
                courseId,
                "Event Sourcing in Practice",
                42,
                1
        );

        fixture.given()
                .events(new CourseCreated(courseId, "Event Sourcing in Practice", 42),
                        new StudentSubscribedToCourse(studentId, courseId))
                .then()
                .await(r -> r.expect(cfg -> assertReadModel(cfg, expectedReadModel)));
    }

    @Test
    void givenCourseCreatedWithStudentSubscribed_WhenStudentUnsubscribedFromCourse_ThenReadModelUpdatedWithDecreasedSubscribedStudents() {
        var courseId = CourseId.random();
        var studentId = StudentId.random();

        CoursesStatsReadModel expectedReadModel = new CoursesStatsReadModel(
                courseId,
                "Event Sourcing in Practice",
                42,
                0
        );

        fixture.given()
                .events(new CourseCreated(courseId, "Event Sourcing in Practice", 42),
                        new StudentSubscribedToCourse(studentId, courseId),
                        new StudentUnsubscribedFromCourse(studentId, courseId))
                .then()
                .await(r -> r.expect(cfg -> assertReadModel(cfg, expectedReadModel)));
    }

    private void assertReadModel(Configuration configuration, CoursesStatsReadModel expectedReadModel) {
        var found = courseStatsRepository(configuration).findById(expectedReadModel.courseId());
        assertThat(found).isNotEmpty();
        assertThat(found).hasValue(expectedReadModel);
    }

    private void assertReadModelNotFound(Configuration configuration, CourseId courseId) {
        var found = courseStatsRepository(configuration).findById(courseId);
        assertThat(found).isEmpty();
    }

    private CourseStatsRepository courseStatsRepository(Configuration configuration) {
        return configuration.getComponent(CourseStatsRepository.class);
    }


}
