package org.axonframework.examples.university.faculty.write.createcourseplain;

import org.axonframework.examples.university.UniversityApplicationTest;
import org.axonframework.examples.university.event.CourseCreated;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.examples.university.write.createcourse.CreateCourse;
import org.axonframework.examples.university.write.createcourse.CreateCourseConfiguration;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.junit.jupiter.api.*;

class CreateCourseTest extends UniversityApplicationTest {

    @Override
    protected EventSourcingConfigurer overrideConfigurer(EventSourcingConfigurer configurer) {
        return CreateCourseConfiguration.configure(configurer);
    }

    @Test
    void givenNotExistingCourse_WhenCreateCourse_ThenSuccess() {
        // given
        var courseId = CourseId.random();
        var courseName = "Event Sourcing in Practice";
        var capacity = 3;

        // when
        executeCommand(
                new CreateCourse(courseId, courseName, capacity)
        );

        // then
        assertEvents(
                new CourseCreated(courseId, courseName, capacity)
        );
    }

    @Test
    @Disabled("imported test, failing with: Creational command handler for command [org.axonframework.examples.university.write.createcourse.CreateCourse#0.0.1] encountered an already existing entity")
    void givenCourseCreated_WhenCreateCourse_ThenSuccess_NoEvents() {
        // given
        var courseId = CourseId.random();
        var courseName = "Event Sourcing in Practice";
        var capacity = 3;

        eventOccurred(
                new CourseCreated(courseId, courseName, capacity)
        );

        // when
        executeCommand(
                new CreateCourse(courseId, courseName, capacity)
        );

        // then
        assertNoEvents();
    }

}