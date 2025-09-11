package io.axoniq.demo.university.faculty.automation.allcoursesfullybookednotifier;

import io.axoniq.demo.university.UniversityApplicationTest;
import io.axoniq.demo.university.shared.application.notifier.NotificationService;
import io.axoniq.demo.university.shared.configuration.NotificationServiceConfiguration;
import io.axoniq.demo.university.shared.infrastructure.notifier.RecordingNotificationService;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class WhenAllCoursesFullyBookedThenSendNotificationTest extends UniversityApplicationTest {

    @Override
    protected EventSourcingConfigurer overrideConfigurer(EventSourcingConfigurer configurer) {
        configurer = NotificationServiceConfiguration.configure(configurer);
        configurer = AllCoursesFullyBookedNotifierConfiguration.configure(configurer);
        return configurer;
    }

    @Test
    void automationTest() {
        // when
        var studentId1 = StudentId.random();
        var studentId2 = StudentId.random();
        var courseId1 = CourseId.random();
        var courseId2 = CourseId.random();

        List<Object> events = List.of(
                new CourseCreated(courseId1, "Course 1", 2),
                new CourseCreated(courseId2, "Course 1", 2),
                new StudentSubscribedToCourse(studentId1, courseId1),
                new StudentSubscribedToCourse(studentId2, courseId1),
                new StudentSubscribedToCourse(studentId1, courseId2),
                new StudentSubscribedToCourse(studentId2, courseId2)
        );
        eventsOccurred(events);

        // then
        var expectedNotification = new NotificationService.Notification("admin", "All courses are fully booked now.");
        var notificationService = (RecordingNotificationService) configuration.getComponent(NotificationService.class);
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(notificationService.sent()).contains(expectedNotification));
    }

}
