package io.axoniq.demo.university.faculty.read.coursestats;

import io.axoniq.demo.university.UniversityApplicationTest;
import io.axoniq.demo.university.faculty.events.*;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.awaitility.Awaitility;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CourseStatsProjectionTest extends UniversityApplicationTest {

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
        CoursesStatsReadModel expectedReadModel = new CoursesStatsReadModel(
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
        CoursesStatsReadModel expectedReadModel = new CoursesStatsReadModel(
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
        CoursesStatsReadModel expectedReadModel = new CoursesStatsReadModel(
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
        var studentId = StudentId.random();
        
        eventOccurred(new CourseCreated(courseId, "Event Sourcing in Practice", 42));
        eventOccurred(new StudentSubscribedToCourse(studentId, courseId));

        // when & then
        CoursesStatsReadModel expectedReadModel = new CoursesStatsReadModel(
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
        var studentId = StudentId.random();
        
        eventOccurred(new CourseCreated(courseId, "Event Sourcing in Practice", 42));
        eventOccurred(new StudentSubscribedToCourse(studentId, courseId));
        eventOccurred(new StudentUnsubscribedFromCourse(studentId, courseId));

        // when & then
        CoursesStatsReadModel expectedReadModel = new CoursesStatsReadModel(
                courseId,
                "Event Sourcing in Practice",
                42,
                0
        );
        assertReadModel(expectedReadModel);
    }

    private void assertReadModel(CoursesStatsReadModel expectedReadModel) {
        Awaitility.await().untilAsserted(() -> {
            var found = courseStatsRepository().findById(expectedReadModel.courseId());
            assertThat(found).isNotEmpty();
            assertThat(found).hasValue(expectedReadModel);
        });
    }

    private CourseStatsRepository courseStatsRepository() {
        return configuration.getComponent(CourseStatsRepository.class);
    }


}
