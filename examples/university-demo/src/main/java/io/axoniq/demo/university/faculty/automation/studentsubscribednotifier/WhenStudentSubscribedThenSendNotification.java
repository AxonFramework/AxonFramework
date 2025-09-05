package io.axoniq.demo.university.faculty.automation.studentsubscribednotifier;

import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.shared.application.notifier.NotificationService;
import org.axonframework.eventhandling.annotation.EventHandler;

/**
 * Automation that reacts on {@link StudentSubscribedToCourse} events and sends a notification.
 * This Event Handler is stateless (so no need to validate some state) and always executes the same action.
 */
public class WhenStudentSubscribedThenSendNotification {

    private final NotificationService notificationService;

    public WhenStudentSubscribedThenSendNotification(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @EventHandler
    void react(StudentSubscribedToCourse event) {
        var notification = new NotificationService.Notification(
                event.studentId().toString(),
                "You have subscribed to course " + event.courseId()
        );
        notificationService.sendNotification(notification);
    }

}