package org.axonframework.examples.university.faculty.write.changecoursecapacity;

import org.axonframework.examples.university.UniversityApplicationTest;
import org.axonframework.examples.university.event.CourseCapacityChanged;
import org.axonframework.examples.university.event.CourseCreated;
import org.axonframework.examples.university.event.CourseRenamed;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.examples.university.write.changecoursecapacity.ChangeCourseCapacity;
import org.axonframework.examples.university.write.changecoursecapacity.ChangeCourseCapacityConfiguration;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeCourseCapacityTest extends UniversityApplicationTest {

    @Override
    protected EventSourcingConfigurer overrideConfigurer(EventSourcingConfigurer configurer) {
        return ChangeCourseCapacityConfiguration.configure(configurer);
    }

    @Test
    void givenNotExistingCourse_WhenChangeCapacity_ThenException() {
        // given
        var courseId = CourseId.random();

        // when-then
        assertThatThrownBy(() -> executeCommand(
                new ChangeCourseCapacity(courseId, 5)
        )).hasMessageContaining("Course with given courseId does not exist");
    }

    @Test
    void givenCourseCreated_WhenChangeCapacity_ThenSuccess() {
        // given
        var courseId = CourseId.random();
        eventOccurred(
                new CourseCreated(courseId, "Event Sourcing in Practice", 42)
        );

        // when
        executeCommand(
                new ChangeCourseCapacity(courseId, 7)
        );

        // then
        assertEvents(
                new CourseCapacityChanged(courseId, 7)
        );
    }

    @Test
    void givenCourseCreated_WhenChangeCapacityToTheSameName_ThenSuccess_NoEvents() {
        // given
        var courseId = CourseId.random();
        eventOccurred(
                new CourseCreated(courseId, "Event Sourcing in Practice", 42)
        );

        // when
        executeCommand(
                new ChangeCourseCapacity(courseId, 42)
        );

        // then
        assertNoEvents();
    }

    @Test
    void givenCourseCreatedAndRenamed_WhenChangeCapacity_ThenSuccess() {
        // given
        var courseId = CourseId.random();
        eventsOccurred(
                new CourseCreated(courseId, "Event Sourcing in Practice", 42),
                new CourseRenamed(courseId, "Event Sourcing in Theory")
        );

        // when
        executeCommand(
                new ChangeCourseCapacity(courseId, 7)
        );

        // then
        assertEvents(
                new CourseCapacityChanged(courseId, 7)
        );
    }

}
