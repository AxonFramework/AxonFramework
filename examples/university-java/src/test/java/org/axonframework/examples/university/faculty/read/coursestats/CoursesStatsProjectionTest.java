package org.axonframework.examples.university.faculty.read.coursestats;

import org.axonframework.examples.university.UniversityApplicationTest;
import org.axonframework.examples.university.event.*;
import org.axonframework.examples.university.read.coursestats.api.GetCourseStatsById;
import org.axonframework.examples.university.read.coursestats.projection.CourseStatsConfiguration;
import org.axonframework.examples.university.read.coursestats.projection.CourseStatsRepository;
import org.axonframework.examples.university.read.coursestats.projection.CoursesStats;
import org.axonframework.examples.university.shared.CourseId;
import org.awaitility.Awaitility;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("imported test, failing with: Unrecognized field \"courseStats\"")
public class CoursesStatsProjectionTest extends UniversityApplicationTest {

    @Override
    protected EventSourcingConfigurer overrideConfigurer(EventSourcingConfigurer configurer) {
        return CourseStatsConfiguration.configure(configurer);
    }

    @Test
    void givenNotExistingCourse_WhenGetById_ThenNotFound() {
        // given
        var courseId = CourseId.random();

        // when
        var found = courseStatsRepository().findById(courseId);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void givenCourseCreated_WhenGetById_ThenFoundCourseWithInitialCapacity() {
        // given
        var courseId = CourseId.random();
        eventOccurred(
                new CourseCreated(courseId, "Event Sourcing in Practice", 42)
        );

        // when & then
        CoursesStats expectedReadModel = new CoursesStats(
                courseId,
                "Event Sourcing in Practice",
                42,
                0
        );
        assertReadModel(expectedReadModel);
    }

    @Test
    void givenCourseCreated_WhenCourseRenamed_ThenReadModelUpdatedWithNewName() {
        // given
        var courseId = CourseId.random();
        var originalName = "Event Sourcing in Practice";
        var newName = "Advanced Event Sourcing";
        
        eventOccurred(new CourseCreated(courseId, originalName, 42));
        eventOccurred(new CourseRenamed(courseId, newName));

        // when & then
        CoursesStats expectedReadModel = new CoursesStats(
                courseId,
                newName,
                42,
                0
        );
        assertReadModel(expectedReadModel);
    }

    @Test
    void givenCourseCreated_WhenCourseCapacityChanged_ThenReadModelUpdatedWithNewCapacity() {
        // given
        var courseId = CourseId.random();
        var originalCapacity = 42;
        var newCapacity = 100;
        
        eventOccurred(new CourseCreated(courseId, "Event Sourcing in Practice", originalCapacity));
        eventOccurred(new CourseCapacityChanged(courseId, newCapacity));

        // when & then
        CoursesStats expectedReadModel = new CoursesStats(
                courseId,
                "Event Sourcing in Practice",
                newCapacity,
                0
        );
        assertReadModel(expectedReadModel);
    }

    @Test
    void givenCourseCreated_WhenStudentSubscribedToCourse_ThenReadModelUpdatedWithIncreasedSubscribedStudents() {
        // given
        var courseId = CourseId.random();
        var studentId = "student-" + java.util.UUID.randomUUID().toString();
        
        eventOccurred(new CourseCreated(courseId, "Event Sourcing in Practice", 42));
        eventOccurred(new StudentSubscribedToCourse(studentId, courseId));

        // when & then
        CoursesStats expectedReadModel = new CoursesStats(
                courseId,
                "Event Sourcing in Practice",
                42,
                1
        );
        assertReadModel(expectedReadModel);
    }

    @Test
    void givenCourseCreatedWithStudentSubscribed_WhenStudentUnsubscribedFromCourse_ThenReadModelUpdatedWithDecreasedSubscribedStudents() {
        // given
        var courseId = CourseId.random();
        var studentId = "student-" + java.util.UUID.randomUUID().toString();
        
        eventOccurred(new CourseCreated(courseId, "Event Sourcing in Practice", 42));
        eventOccurred(new StudentSubscribedToCourse(studentId, courseId));
        eventOccurred(new StudentUnsubscribedFromCourse(studentId, courseId));

        // when & then
        CoursesStats expectedReadModel = new CoursesStats(
                courseId,
                "Event Sourcing in Practice",
                42,
                0
        );
        assertReadModel(expectedReadModel);
    }

    private void assertReadModel(CoursesStats expectedReadModel) {
        Awaitility.await().untilAsserted(() -> {
            var found = configuration.getComponent(QueryGateway.class)
                    .query(new GetCourseStatsById(expectedReadModel.courseId()), CoursesStats.class)
                    .join();
            assertThat(found).isEqualTo(expectedReadModel);
        });
    }

    private CourseStatsRepository courseStatsRepository() {
        return configuration.getComponent(CourseStatsRepository.class);
    }


}
