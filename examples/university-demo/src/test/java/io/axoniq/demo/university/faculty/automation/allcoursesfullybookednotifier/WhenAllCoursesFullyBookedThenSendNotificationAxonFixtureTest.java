package io.axoniq.demo.university.faculty.automation.allcoursesfullybookednotifier;

import io.axoniq.demo.university.faculty.FacultyAxonTestFixture;
import io.axoniq.demo.university.shared.application.notifier.NotificationService;
import io.axoniq.demo.university.shared.configuration.NotificationServiceConfiguration;
import io.axoniq.demo.university.shared.infrastructure.notifier.RecordingNotificationService;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.configuration.Configuration;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WhenAllCoursesFullyBookedThenSendNotificationAxonFixtureTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        fixture = FacultyAxonTestFixture.slice(configurer -> {
            configurer = NotificationServiceConfiguration.configure(configurer);
            return AllCoursesFullyBookedNotifierConfiguration.configure(configurer);
        });
    }

    @AfterEach
    void afterEach() {
        fixture.stop();
    }

    @Test
    void automationTest() {
        var studentId1 = StudentId.random();
        var studentId2 = StudentId.random();
        var courseId1 = CourseId.random();
        var courseId2 = CourseId.random();

        var expectedNotification = new NotificationService.Notification("admin", "All courses are fully booked now.");

        fixture.given()
                .events(
                        new CourseCreated(courseId1, "Course 1", 2), // Create course with capacity 2
                        new CourseCreated(courseId2, "Course 2", 2), // Create course with capacity 2
                        new StudentSubscribedToCourse(studentId1, courseId1), // Fill first course
                        new StudentSubscribedToCourse(studentId2, courseId1),
                        new StudentSubscribedToCourse(studentId1, courseId2), // Fill second course
                        new StudentSubscribedToCourse(studentId2, courseId2)  // This should trigger notification
                )
                .then()
                .await(r -> r.expect(cfg -> assertNotificationSent(cfg, expectedNotification)));
    }

    private void assertNotificationSent(Configuration configuration, NotificationService.Notification expectedNotification) {
        var notificationService = (RecordingNotificationService) configuration.getComponent(NotificationService.class);
        assertThat(notificationService.sent()).contains(expectedNotification);
    }

}
