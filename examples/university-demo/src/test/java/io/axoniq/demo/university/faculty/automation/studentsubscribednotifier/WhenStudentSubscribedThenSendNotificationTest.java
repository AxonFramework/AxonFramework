package io.axoniq.demo.university.faculty.automation.studentsubscribednotifier;

import io.axoniq.demo.university.UniversityApplicationTest;
import io.axoniq.demo.university.shared.configuration.NotificationServiceConfiguration;
import io.axoniq.demo.university.shared.infrastructure.notifier.RecordingNotificationService;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.shared.application.notifier.NotificationService;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

public class WhenStudentSubscribedThenSendNotificationTest extends UniversityApplicationTest {

    @Override
    protected EventSourcingConfigurer overrideConfigurer(EventSourcingConfigurer configurer) {
        configurer = NotificationServiceConfiguration.configure(configurer);
        configurer = StudentSubscribedNotifierConfiguration.configure(configurer);
        return configurer;
    }

    @Test
    void automationTest() {
        // when
        var studentId = StudentId.random();
        var courseId = CourseId.random();
        eventsOccurred(new StudentSubscribedToCourse(studentId, courseId));

        // then
        var expectedNotification = new NotificationService.Notification(studentId.raw(), "You have subscribed to course " + courseId);
        var notificationService = (RecordingNotificationService) configuration.getComponent(NotificationService.class);
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(notificationService.sent()).contains(expectedNotification));
    }

}
