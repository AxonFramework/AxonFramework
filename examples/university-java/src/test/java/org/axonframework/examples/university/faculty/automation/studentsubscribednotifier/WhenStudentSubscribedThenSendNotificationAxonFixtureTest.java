package org.axonframework.examples.university.faculty.automation.studentsubscribednotifier;

import org.axonframework.examples.university.faculty.FacultyAxonTestFixture;
import org.axonframework.examples.university.event.StudentSubscribedToCourse;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.examples.university.shared.notification.NotificationService;
import org.axonframework.examples.university.shared.notification.RecordingNotificationService;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

public class WhenStudentSubscribedThenSendNotificationAxonFixtureTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        fixture = FacultyAxonTestFixture.slice(configurer -> {
            configurer = configurer.componentRegistry(cr -> cr.registerComponent(
                    NotificationService.class,
                    cfg -> new RecordingNotificationService())
            );
            return configurer;
        });
    }

    @AfterEach
    void afterEach() {
        fixture.stop();
    }

    @Test
    @Disabled("imported test, failing with: ConditionTimeoutException")
    void automationTest() {
        var studentId = "student-" + java.util.UUID.randomUUID().toString();
        var courseId = CourseId.random();

        var expectedNotification = new NotificationService.Notification(studentId, "You have subscribed to course " + courseId);

        fixture.given()
                .events(new StudentSubscribedToCourse(studentId, courseId))
                .then()
                .await(r -> r.expect(cfg -> assertNotificationSent(cfg, expectedNotification)));
    }

    private void assertNotificationSent(Configuration configuration, NotificationService.Notification expectedNotification) {
        var notificationService = (RecordingNotificationService) configuration.getComponent(NotificationService.class);
        assertThat(notificationService.sent()).contains(expectedNotification);
    }

}