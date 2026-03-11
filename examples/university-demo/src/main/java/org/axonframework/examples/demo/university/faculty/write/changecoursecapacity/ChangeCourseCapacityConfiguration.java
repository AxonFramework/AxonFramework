package org.axonframework.examples.demo.university.faculty.write.changecoursecapacity;

import org.axonframework.examples.demo.university.shared.ids.CourseId;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

public class ChangeCourseCapacityConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityModule
                .autodetected(CourseId.class, ChangeCourseCapacityCommandHandler.State.class);

        var commandHandlingModule = CommandHandlingModule
                .named("ChangeCourseCapacity")
                .commandHandlers()
                .autodetectedCommandHandlingComponent(c -> new ChangeCourseCapacityCommandHandler());

        return configurer
                .registerEntity(stateEntity)
                .registerCommandHandlingModule(commandHandlingModule);
    }

    private ChangeCourseCapacityConfiguration() {
        // Prevent instantiation
    }

}
