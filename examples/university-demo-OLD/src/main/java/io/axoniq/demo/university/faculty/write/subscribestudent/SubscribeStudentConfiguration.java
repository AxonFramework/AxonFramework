package io.axoniq.demo.university.faculty.write.subscribestudent;

import org.axonframework.eventsourcing.configuration.EventSourcedEntityBuilder;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;

public class SubscribeStudentConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityBuilder
                .annotatedEntity(SubscriptionId.class, SubscribeStudentToCourseCommandHandler.State.class);
        var commandHandlingModule = StatefulCommandHandlingModule
                .named("SubscribeStudent")
                .entities()
                .entity(stateEntity)
                .commandHandlers()
                .annotatedCommandHandlingComponent(c -> new SubscribeStudentToCourseCommandHandler());
        return configurer.registerStatefulCommandHandlingModule(commandHandlingModule);
    }

    private SubscribeStudentConfiguration() {
        // Prevent instantiation
    }

}
