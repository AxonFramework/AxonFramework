package org.axonframework.examples.university.write.subscribestudent;

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;

public class SubscribeStudentConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityModule
                .autodetected(SubscriptionId.class, SubscribeStudentToCourseCommandHandler.State.class);
        var commandHandlingModule = CommandHandlingModule
                .named("SubscribeStudent")
                .commandHandlers()
                .annotatedCommandHandlingComponent(c -> new SubscribeStudentToCourseCommandHandler());
        return configurer
                .registerEntity(stateEntity)
                .registerCommandHandlingModule(commandHandlingModule);
    }

    private SubscribeStudentConfiguration() {
        // Prevent instantiation
    }

}
