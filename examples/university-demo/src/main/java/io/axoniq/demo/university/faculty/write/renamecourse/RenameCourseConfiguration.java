package io.axoniq.demo.university.faculty.write.renamecourse;

import io.axoniq.demo.university.faculty.write.CourseId;
import org.axonframework.commandhandling.annotation.AnnotatedCommandHandlingComponent;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityBuilder;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;

public class RenameCourseConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityBuilder
                .annotatedEntity(CourseId.class, RenameCourseCommandHandler.State.class);
        var commandHandlingModule = StatefulCommandHandlingModule
                .named("RenameCourse")
                .entities()
                .entity(stateEntity)
                .commandHandlers()
                .commandHandlingComponent(c -> new AnnotatedCommandHandlingComponent<>(new RenameCourseCommandHandler()));
        return configurer.registerStatefulCommandHandlingModule(commandHandlingModule);
    }
}
