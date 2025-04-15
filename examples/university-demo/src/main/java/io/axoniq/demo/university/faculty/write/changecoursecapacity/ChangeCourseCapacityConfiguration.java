package io.axoniq.demo.university.faculty.write.changecoursecapacity;

import io.axoniq.demo.university.shared.ids.CourseId;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityBuilder;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;

public class ChangeCourseCapacityConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityBuilder
                .annotatedEntity(CourseId.class, ChangeCourseCapacityCommandHandler.State.class);

        var commandHandlingModule = StatefulCommandHandlingModule
                .named("ChangeCourseCapacity")
                .entities()
                .entity(stateEntity)
                .commandHandlers()
                .annotatedCommandHandlingComponent(c -> new ChangeCourseCapacityCommandHandler());

        return configurer.registerStatefulCommandHandlingModule(commandHandlingModule);
    }

}
