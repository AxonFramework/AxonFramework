package io.axoniq.demo.university.faculty.write.createcourse;

import io.axoniq.demo.university.faculty.write.CourseId;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityBuilder;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;

public class CreateCourseConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityBuilder
                .annotatedEntity(CourseId.class, CreateCourseCommandHandler.State.class);
        var commandHandlingModule = StatefulCommandHandlingModule
                .named("CreateCourse")
                .entities()
                .entity(stateEntity)
                .commandHandlers()
                .commandHandler(new QualifiedName(CreateCourse.class),
                                c -> new CreateCourseCommandHandler(c.getComponent(
                                        EventSink.class)));
        return configurer.registerStatefulCommandHandlingModule(commandHandlingModule);
    }
}
