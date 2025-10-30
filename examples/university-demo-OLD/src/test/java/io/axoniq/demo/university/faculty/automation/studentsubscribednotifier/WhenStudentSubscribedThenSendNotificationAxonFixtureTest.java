package io.axoniq.demo.university.faculty.automation.studentsubscribednotifier;

import io.axoniq.demo.university.faculty.FacultyAxonTestFixture;
import io.axoniq.demo.university.faculty.Ids;
import io.axoniq.demo.university.shared.configuration.NotificationServiceConfiguration;
import io.axoniq.demo.university.shared.infrastructure.notifier.RecordingNotificationService;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.shared.application.notifier.NotificationService;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.configuration.Configuration;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WhenStudentSubscribedThenSendNotificationAxonFixtureTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        fixture = FacultyAxonTestFixture.slice(configurer -> {
            configurer = NotificationServiceConfiguration.configure(configurer);
            return StudentSubscribedNotifierConfiguration.configure(configurer);
        });
    }

    @AfterEach
    void afterEach() {
        fixture.stop();
    }

    @Test
    void automationTest() {
        var studentId = StudentId.random();
        var courseId = CourseId.random();

        var expectedNotification = new NotificationService.Notification(studentId.raw(), "You have subscribed to course " + courseId);

        fixture.given()
                .events(new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId, courseId))
                .then()
                .await(r -> r.expect(cfg -> assertNotificationSent(cfg, expectedNotification)));
    }

    private void assertNotificationSent(Configuration configuration, NotificationService.Notification expectedNotification) {
        var notificationService = (RecordingNotificationService) configuration.getComponent(NotificationService.class);
        assertThat(notificationService.sent()).contains(expectedNotification);
    }

}