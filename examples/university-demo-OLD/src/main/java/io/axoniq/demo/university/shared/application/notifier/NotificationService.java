package io.axoniq.demo.university.shared.application.notifier;

public interface NotificationService {

    record Notification(String recipientId, String message) {
    }

    void sendNotification(Notification notification);

}
