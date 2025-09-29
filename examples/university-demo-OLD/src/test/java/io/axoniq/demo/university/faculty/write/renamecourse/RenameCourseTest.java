package io.axoniq.demo.university.faculty.write.renamecourse;

import io.axoniq.demo.university.UniversityApplicationTest;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.CourseRenamed;
import io.axoniq.demo.university.shared.ids.CourseId;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenameCourseTest extends UniversityApplicationTest {

    @Override
    protected EventSourcingConfigurer overrideConfigurer(EventSourcingConfigurer configurer) {
        return RenameCourseConfiguration.configure(configurer);
    }

    @Test
    void givenNotExistingCourse_WhenRenameCourse_ThenException() {
        // given
        var courseId = CourseId.random();

        // when-then
        assertThatThrownBy(() -> executeCommand(
                new RenameCourse(courseId, "Event Sourcing in Practice")
        )).hasMessageContaining("Course with given id does not exist");
    }

    @Test
    void givenCourseCreated_WhenRenameCourse_ThenSuccess() {
        // given
        var courseId = CourseId.random();
        eventOccurred(
                new CourseCreated(courseId, "Event Sourcing in Practice", 42)
        );

        // when
        executeCommand(
                new RenameCourse(courseId, "Event Sourcing in Theory")
        );

        // then
        assertEvents(
                new CourseRenamed(courseId, "Event Sourcing in Theory")
        );
    }

    @Test
    void givenCourseCreated_WhenRenameCourseToTheSameName_ThenSuccess_NoEvents() {
        // given
        var courseId = CourseId.random();
        eventOccurred(
                new CourseCreated(courseId, "Event Sourcing in Practice", 42)
        );

        // when
        executeCommand(
                new RenameCourse(courseId, "Event Sourcing in Practice")
        );

        // then
        assertNoEvents();
    }

    @Test
    void givenCourseCreatedAndRenamed_WhenRenameCourse_ThenSuccess() {
        // given
        var courseId = CourseId.random();
        eventsOccurred(
                new CourseCreated(courseId, "Event Sourcing in Practice", 42),
                new CourseRenamed(courseId, "Event Sourcing in Theory")
        );

        // when
        executeCommand(
                new RenameCourse(courseId, "Theoretical Practice of Event Sourcing")
        );

        // then
        assertEvents(
                new CourseRenamed(courseId, "Theoretical Practice of Event Sourcing")
        );
    }

}