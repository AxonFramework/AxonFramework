package io.axoniq.demo.university.faculty.write.renamecourse;

import io.axoniq.demo.university.shared.ids.CourseId;
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
                .annotatedCommandHandlingComponent(c -> new RenameCourseCommandHandler());
        return configurer.registerStatefulCommandHandlingModule(commandHandlingModule);
    }

}
