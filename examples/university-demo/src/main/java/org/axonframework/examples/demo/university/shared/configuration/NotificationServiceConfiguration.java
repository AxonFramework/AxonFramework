package org.axonframework.examples.demo.university.shared.configuration;

import org.axonframework.examples.demo.university.shared.application.notifier.NotificationService;
import org.axonframework.examples.demo.university.shared.infrastructure.notifier.LoggingNotificationService;
import org.axonframework.examples.demo.university.shared.infrastructure.notifier.RecordingNotificationService;
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
