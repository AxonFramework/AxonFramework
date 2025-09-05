package io.axoniq.demo.university.shared.configuration;

import io.axoniq.demo.university.shared.application.notifier.NotificationService;
import io.axoniq.demo.university.shared.infrastructure.notifier.LoggingNotificationService;
import io.axoniq.demo.university.shared.infrastructure.notifier.RecordingNotificationService;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

public class NotificationServiceConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        return configurer.componentRegistry(cr -> cr.registerComponent(
                NotificationService.class,
                cfg -> new RecordingNotificationService(new LoggingNotificationService()))
        );
    }

    private NotificationServiceConfiguration() {
        // Prevent instantiation
    }
}
