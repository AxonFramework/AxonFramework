package io.axoniq.demo.university.faculty.write.createcourse;

import io.axoniq.demo.university.shared.ids.CourseId;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
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

        var courseNameUniqueNameSetValidation = EventProcessorModule
                .subscribing("CourseNameUniqueNameSetValidation")
                .eventHandlingComponents(eh -> eh.annotated(cfg -> new CourseUniqueNameSetValidation()))
                .notCustomized();

        return configurer
                .registerEntity(stateEntity)
                .registerCommandHandlingModule(commandHandlingModule)
                .messaging(ms -> ms.eventProcessing(ep -> ep.subscribing(s -> s.processor(courseNameUniqueNameSetValidation))));
    }

    private CreateCourseConfiguration() {
        // Prevent instantiation
    }

}
