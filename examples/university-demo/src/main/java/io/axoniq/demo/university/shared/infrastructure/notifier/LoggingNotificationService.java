package io.axoniq.demo.university.shared.infrastructure.notifier;

import io.axoniq.demo.university.shared.application.notifier.NotificationService;

import java.util.logging.Logger;

public class LoggingNotificationService implements NotificationService {

    private static final Logger logger = Logger.getLogger(LoggingNotificationService.class.getName());

    @Override
    public void sendNotification(Notification notification) {
        logger.info("Sending notification to " + notification.recipientId() + ": " + notification.message());
    }
}
