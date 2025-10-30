package io.axoniq.demo.university.faculty.automation.studentsubscribednotifierplain;

import io.axoniq.demo.university.UniversityApplicationTest;
import io.axoniq.demo.university.faculty.Ids;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.shared.application.notifier.NotificationService;
import io.axoniq.demo.university.shared.configuration.NotificationServiceConfiguration;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import io.axoniq.demo.university.shared.infrastructure.notifier.RecordingNotificationService;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
        eventsOccurred(new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId, courseId));

        // then
        var expectedNotification = new NotificationService.Notification(studentId.raw(), "You have subscribed to course " + courseId);
        var notificationService = (RecordingNotificationService) configuration.getComponent(NotificationService.class);
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(notificationService.sent()).contains(expectedNotification));
    }

}
