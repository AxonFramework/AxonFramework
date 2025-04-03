package io.axoniq.demo.university.faculty.write.unsubscribestudent;

import org.axonframework.commandhandling.annotation.AnnotatedCommandHandlingComponent;
import org.axonframework.config.ConfigurationParameterResolverFactory;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityBuilder;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.command.annotation.InjectEntityParameterResolverFactory;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;

import java.util.List;

public class UnsubscribeStudentConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityBuilder
                .annotatedEntity(SubscriptionId.class, UnsubscribeStudentCommandHandler.State.class);
        var commandHandlingModule = StatefulCommandHandlingModule
                .named("UnsubscribeStudent")
                .entities()
                .entity(stateEntity)
                .commandHandlers()
                .commandHandlingComponent(c -> new AnnotatedCommandHandlingComponent<>(new UnsubscribeStudentCommandHandler(),
                                                                                       parameterResolverFactory(c)));
        return configurer.registerStatefulCommandHandlingModule(commandHandlingModule);
    }

    private static MultiParameterResolverFactory parameterResolverFactory(NewConfiguration configuration) {
        return MultiParameterResolverFactory.ordered(List.of(
                ClasspathParameterResolverFactory.forClass(UnsubscribeStudentConfiguration.class),
                // To be able to get components
                new ConfigurationParameterResolverFactory(configuration),
                // To be able to get the entity, the StateManager needs to be available.
                // When the new configuration API is there, we should have a way to resolve this
                new InjectEntityParameterResolverFactory(configuration.getComponent(StateManager.class))
        ));
    }
}
