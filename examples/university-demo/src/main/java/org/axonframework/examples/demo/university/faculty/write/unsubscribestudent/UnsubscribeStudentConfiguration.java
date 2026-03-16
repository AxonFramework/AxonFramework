package org.axonframework.examples.demo.university.faculty.write.unsubscribestudent;

import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

public class UnsubscribeStudentConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityModule
                .autodetected(SubscriptionId.class, UnsubscribeStudentFromCourseCommandHandler.State.class);

        var commandHandlingModule = CommandHandlingModule
                .named("UnsubscribeStudent")
                .commandHandlers()
                .autodetectedCommandHandlingComponent(c -> new UnsubscribeStudentFromCourseCommandHandler());

        return configurer
                .registerEntity(stateEntity)
                .registerCommandHandlingModule(commandHandlingModule);
    }

    private UnsubscribeStudentConfiguration() {
        // Prevent instantiation
    }

}
