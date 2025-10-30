package io.axoniq.demo.university.faculty.read.coursestats;

import io.axoniq.demo.university.faculty.FacultyAxonTestFixture;
import io.axoniq.demo.university.faculty.Ids;
import io.axoniq.demo.university.faculty.events.*;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.configuration.Configuration;
import org.axonframework.queryhandling.gateway.QueryGateway;
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
                .expect(cfg -> assertCourseStatsNotExist(cfg, courseId));
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
                .events(new CourseCreated(Ids.FACULTY_ID, courseId, "Event Sourcing in Practice", 42))
                .then()
                .await(r -> r.expect(cfg -> assertCourseStats(cfg, expectedReadModel)));
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
                .events(new CourseCreated(Ids.FACULTY_ID, courseId, originalName, 42), new CourseRenamed(Ids.FACULTY_ID, courseId, newName))
                .then()
                .await(r -> r.expect(cfg -> assertCourseStats(cfg, expectedReadModel)));
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
                .events(new CourseCreated(Ids.FACULTY_ID, courseId, "Event Sourcing in Practice", originalCapacity),
                        new CourseCapacityChanged(Ids.FACULTY_ID, courseId, newCapacity))
                .then()
                .await(r -> r.expect(cfg -> assertCourseStats(cfg, expectedReadModel)));
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
                .events(new CourseCreated(Ids.FACULTY_ID, courseId, "Event Sourcing in Practice", 42),
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId, courseId))
                .then()
                .await(r -> r.expect(cfg -> assertCourseStats(cfg, expectedReadModel)));
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
                .events(new CourseCreated(Ids.FACULTY_ID, courseId, "Event Sourcing in Practice", 42),
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId, courseId),
                        new StudentUnsubscribedFromCourse(Ids.FACULTY_ID, studentId, courseId))
                .then()
                .await(r -> r.expect(cfg -> assertCourseStats(cfg, expectedReadModel)));
    }

    private void assertCourseStats(Configuration configuration, CoursesStatsReadModel expectedReadModel) {
        var found = configuration.getComponent(QueryGateway.class)
                .query(new GetCourseStatsById(expectedReadModel.courseId()), GetCourseStatsById.Result.class, null)
                .join();
        assertThat(found).isNotNull();
        assertThat(found.stats()).isEqualTo(expectedReadModel);
    }

    private void assertCourseStatsNotExist(Configuration configuration, CourseId courseId) {
        var found = configuration.getComponent(QueryGateway.class)
                .query(new GetCourseStatsById(courseId), GetCourseStatsById.Result.class, null)
                .join();
        assertThat(found.stats()).isNull();
    }

}
