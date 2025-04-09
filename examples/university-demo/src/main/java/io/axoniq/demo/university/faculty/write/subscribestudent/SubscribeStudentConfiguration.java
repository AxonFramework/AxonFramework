package io.axoniq.demo.university.faculty.write.subscribestudent;

import org.axonframework.eventsourcing.configuration.EventSourcedEntityBuilder;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;

public class SubscribeStudentConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityBuilder
                .annotatedEntity(SubscriptionId.class, SubscribeStudentCommandHandler.State.class);
        var commandHandlingModule = StatefulCommandHandlingModule
                .named("SubscribeStudent")
                .entities()
                .entity(stateEntity)
                .commandHandlers()
                .annotatedCommandHandlingComponent(c -> new SubscribeStudentCommandHandler());
        return configurer.registerStatefulCommandHandlingModule(commandHandlingModule);
    }

}
