package io.axoniq.demo.university.faculty.write.changecoursecapacity;

import io.axoniq.demo.university.shared.ids.CourseId;
import org.axonframework.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

public class ChangeCourseCapacityConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityModule
                .annotated(CourseId.class, ChangeCourseCapacityCommandHandler.State.class);

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
