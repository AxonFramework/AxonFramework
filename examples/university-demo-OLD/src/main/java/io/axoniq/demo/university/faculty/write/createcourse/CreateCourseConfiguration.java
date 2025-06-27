package io.axoniq.demo.university.faculty.write.createcourse;

import io.axoniq.demo.university.shared.ids.CourseId;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;

public class CreateCourseConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityModule
                .annotated(CourseId.class, CreateCourseCommandHandler.State.class);

        var commandHandlingModule = StatefulCommandHandlingModule
                .named("CreateCourse")
                .entities()
                .entity(stateEntity)
                .commandHandlers()
                .annotatedCommandHandlingComponent(c -> new CreateCourseCommandHandler());

        return configurer.registerStatefulCommandHandlingModule(commandHandlingModule);
    }

    private CreateCourseConfiguration() {
        // Prevent instantiation
    }

}
