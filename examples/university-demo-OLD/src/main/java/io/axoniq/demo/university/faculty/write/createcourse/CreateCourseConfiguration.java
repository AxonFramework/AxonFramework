package io.axoniq.demo.university.faculty.write.createcourse;

import io.axoniq.demo.university.shared.ids.CourseId;
import org.axonframework.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

public class CreateCourseConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityModule
                .annotated(CourseId.class, CreateCourseCommandHandler.State.class);

        var commandHandlingModule = CommandHandlingModule
                .named("CreateCourse")
                .commandHandlers()
                .annotatedCommandHandlingComponent(c -> new CreateCourseCommandHandler());
        return configurer
                .registerEntity(stateEntity)
                .registerCommandHandlingModule(commandHandlingModule);
    }

    private CreateCourseConfiguration() {
        // Prevent instantiation
    }

}
