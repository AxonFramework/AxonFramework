package io.axoniq.demo.university.faculty.write.subscribestudent;

import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

public class SubscribeStudentConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityModule
                .annotated(SubscriptionId.class, SubscribeStudentToCourseCommandHandler.State.class);
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
