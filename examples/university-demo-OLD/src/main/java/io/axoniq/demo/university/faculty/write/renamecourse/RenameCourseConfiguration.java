package io.axoniq.demo.university.faculty.write.renamecourse;

import io.axoniq.demo.university.shared.ids.CourseId;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

public class RenameCourseConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityModule
                .autodetected(CourseId.class, RenameCourseCommandHandler.State.class);

        var commandHandlingModule = CommandHandlingModule
                .named("RenameCourse")
                .commandHandlers()
                .annotatedCommandHandlingComponent(c -> new RenameCourseCommandHandler());
        return configurer
                .registerEntity(stateEntity)
                .registerCommandHandlingModule(commandHandlingModule);
    }

    private RenameCourseConfiguration() {
        // Prevent instantiation
    }

}
