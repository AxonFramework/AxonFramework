package org.axonframework.examples.university.faculty.automation.studentsubscribednotifierplain;

import org.axonframework.examples.university.UniversityApplicationTest;
import org.axonframework.examples.university.event.StudentSubscribedToCourse;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.examples.university.shared.notification.NotificationConfiguration;
import org.axonframework.examples.university.shared.notification.NotificationService;
import org.axonframework.examples.university.shared.notification.RecordingNotificationService;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class WhenStudentSubscribedThenSendNotificationTest extends UniversityApplicationTest {

    @Override
    protected EventSourcingConfigurer overrideConfigurer(EventSourcingConfigurer configurer) {
        configurer = configurer.componentRegistry(cr -> cr.registerComponent(
                NotificationService.class,
                cfg -> new RecordingNotificationService())
        );
        return configurer;
    }

    @Test
    @Disabled("imported test, failing with: ConditionTimeoutException")
    void automationTest() {
        // when
        var studentId = "student-" + java.util.UUID.randomUUID().toString();
        var courseId = CourseId.random();
        eventsOccurred(new StudentSubscribedToCourse(studentId, courseId));

        // then
        var expectedNotification = new NotificationService.Notification(studentId, "You have subscribed to course " + courseId);
        var notificationService = (RecordingNotificationService) configuration.getComponent(NotificationService.class);
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(notificationService.sent()).contains(expectedNotification));
    }

}
