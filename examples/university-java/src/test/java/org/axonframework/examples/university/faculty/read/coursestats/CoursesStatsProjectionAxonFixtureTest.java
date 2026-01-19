package org.axonframework.examples.university.faculty.read.coursestats;

import org.axonframework.examples.university.faculty.FacultyAxonTestFixture;
import org.axonframework.examples.university.event.*;
import org.axonframework.examples.university.read.coursestats.api.GetCourseStatsById;
import org.axonframework.examples.university.read.coursestats.projection.CourseStatsConfiguration;
import org.axonframework.examples.university.read.coursestats.projection.CoursesStats;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("imported test, failing with: Unrecognized field \"courseStats\"")
public class CoursesStatsProjectionAxonFixtureTest {

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
                .expect(cfg -> assertCoursesStatsNotExist(cfg, courseId));
    }

    @Test
    void givenCourseCreated_WhenGetById_ThenFoundCourseWithInitialCapacity() {
        var courseId = CourseId.random();
        CoursesStats expectedReadModel = new CoursesStats(
                courseId,
                "Event Sourcing in Practice",
                42,
                0
        );

        fixture.given()
                .events(new CourseCreated(courseId, "Event Sourcing in Practice", 42))
                .then()
                .await(r -> r.expect(cfg -> assertCoursesStats(cfg, expectedReadModel)));
    }

    @Test
    void givenCourseCreated_WhenCourseRenamed_ThenReadModelUpdatedWithNewName() {
        var courseId = CourseId.random();
        var originalName = "Event Sourcing in Practice";
        var newName = "Advanced Event Sourcing";

        CoursesStats expectedReadModel = new CoursesStats(
                courseId,
                newName,
                42,
                0
        );

        fixture.given()
                .events(new CourseCreated(courseId, originalName, 42), new CourseRenamed(courseId, newName))
                .then()
                .await(r -> r.expect(cfg -> assertCoursesStats(cfg, expectedReadModel)));
    }

    @Test
    void givenCourseCreated_WhenCourseCapacityChanged_ThenReadModelUpdatedWithNewCapacity() {
        var courseId = CourseId.random();
        var originalCapacity = 42;
        var newCapacity = 100;

        CoursesStats expectedReadModel = new CoursesStats(
                courseId,
                "Event Sourcing in Practice",
                newCapacity,
                0
        );

        fixture.given()
                .events(new CourseCreated(courseId, "Event Sourcing in Practice", originalCapacity),
                        new CourseCapacityChanged(courseId, newCapacity))
                .then()
                .await(r -> r.expect(cfg -> assertCoursesStats(cfg, expectedReadModel)));
    }

    @Test
    void givenCourseCreated_WhenStudentSubscribedToCourse_ThenReadModelUpdatedWithIncreasedSubscribedStudents() {
        var courseId = CourseId.random();
        var studentId = "student-" + java.util.UUID.randomUUID().toString();

        CoursesStats expectedReadModel = new CoursesStats(
                courseId,
                "Event Sourcing in Practice",
                42,
                1
        );

        fixture.given()
                .events(new CourseCreated(courseId, "Event Sourcing in Practice", 42),
                        new StudentSubscribedToCourse(studentId, courseId))
                .then()
                .await(r -> r.expect(cfg -> assertCoursesStats(cfg, expectedReadModel)));
    }

    @Test
    void givenCourseCreatedWithStudentSubscribed_WhenStudentUnsubscribedFromCourse_ThenReadModelUpdatedWithDecreasedSubscribedStudents() {
        var courseId = CourseId.random();
        var studentId = "student-" + java.util.UUID.randomUUID().toString();

        CoursesStats expectedReadModel = new CoursesStats(
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
                .await(r -> r.expect(cfg -> assertCoursesStats(cfg, expectedReadModel)));
    }

    private void assertCoursesStats(Configuration configuration, CoursesStats expectedReadModel) {
        var found = configuration.getComponent(QueryGateway.class)
                .query(new GetCourseStatsById(expectedReadModel.courseId()), CoursesStats.class)
                .join();
        assertThat(found).isEqualTo(expectedReadModel);
    }

    private void assertCoursesStatsNotExist(Configuration configuration, CourseId courseId) {
        var found = configuration.getComponent(QueryGateway.class)
                .query(new GetCourseStatsById(courseId), CoursesStats.class)
                .join();
        assertThat(found).isNull();
    }

}
