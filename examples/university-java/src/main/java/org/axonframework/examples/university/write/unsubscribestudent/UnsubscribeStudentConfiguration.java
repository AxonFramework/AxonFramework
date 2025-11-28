package org.axonframework.examples.university.write.unsubscribestudent;

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;

public class UnsubscribeStudentConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityModule
                .autodetected(SubscriptionId.class, UnsubscribeStudentFromCourseCommandHandler.State.class);

        var commandHandlingModule = CommandHandlingModule
                .named("UnsubscribeStudent")
                .commandHandlers()
                .annotatedCommandHandlingComponent(c -> new UnsubscribeStudentFromCourseCommandHandler());

        return configurer
                .registerEntity(stateEntity)
                .registerCommandHandlingModule(commandHandlingModule);
    }

    private UnsubscribeStudentConfiguration() {
        // Prevent instantiation
    }

}
