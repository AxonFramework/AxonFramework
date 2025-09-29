package io.axoniq.demo.university.faculty.automation.studentsubscribednotifierplain;

import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.shared.application.notifier.NotificationService;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.conversion.MessageConverter;
import org.axonframework.messaging.unitofwork.ProcessingContext;

/**
 * Automation that reacts on {@link StudentSubscribedToCourse} events and sends a notification.
 * This Event Handler is stateless (so no need to validate some state) and always executes the same action.
 */
class WhenStudentSubscribedThenSendNotification {

    static MessageStream.Empty<Message> react(EventMessage event, ProcessingContext context) {
        var converter = context.component(MessageConverter.class);
        var payload = event.payloadAs(StudentSubscribedToCourse.class, converter);
        var notificationService = context.component(NotificationService.class);
        var notification = new NotificationService.Notification(
                payload.studentId().toString(),
                "You have subscribed to course " + payload.courseId()
        );
        notificationService.sendNotification(notification);
        return MessageStream.empty();
    }

}