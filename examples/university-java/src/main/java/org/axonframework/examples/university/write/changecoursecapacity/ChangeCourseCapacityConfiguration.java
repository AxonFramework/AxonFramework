package org.axonframework.examples.university.write.changecoursecapacity;

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;

public class ChangeCourseCapacityConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityModule
                .autodetected(CourseId.class, ChangeCourseCapacityCommandHandler.State.class);

        var commandHandlingModule = CommandHandlingModule
                .named("ChangeCourseCapacity")
                .commandHandlers()
                .annotatedCommandHandlingComponent(c -> new ChangeCourseCapacityCommandHandler());

        return configurer
                .registerEntity(stateEntity)
                .registerCommandHandlingModule(commandHandlingModule);
    }

    private ChangeCourseCapacityConfiguration() {
        // Prevent instantiation
    }

}
