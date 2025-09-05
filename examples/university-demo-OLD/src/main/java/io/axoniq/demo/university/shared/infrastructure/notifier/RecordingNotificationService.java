package io.axoniq.demo.university.shared.infrastructure.notifier;

import io.axoniq.demo.university.shared.application.notifier.NotificationService;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RecordingNotificationService implements NotificationService {

    private final NotificationService delegate;
    private final ConcurrentLinkedQueue<Notification> recorded = new ConcurrentLinkedQueue<>();

    public RecordingNotificationService(NotificationService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void sendNotification(Notification notification) {
        delegate.sendNotification(notification);
        recorded.add(notification);
    }

    public List<Notification> sent() {
        return List.copyOf(recorded);
    }
}
