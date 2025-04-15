package io.axoniq.demo.university.faculty.write.unsubscribestudent;

import org.axonframework.eventsourcing.configuration.EventSourcedEntityBuilder;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;

public class UnsubscribeStudentConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityBuilder
                .annotatedEntity(SubscriptionId.class, UnsubscribeStudentFromCourseCommandHandler.State.class);
        var commandHandlingModule = StatefulCommandHandlingModule
                .named("UnsubscribeStudent")
                .entities()
                .entity(stateEntity)
                .commandHandlers()
                .annotatedCommandHandlingComponent(c -> new UnsubscribeStudentFromCourseCommandHandler());
        return configurer.registerStatefulCommandHandlingModule(commandHandlingModule);
    }

    private UnsubscribeStudentConfiguration() {
        // Prevent instantiation
    }

}
