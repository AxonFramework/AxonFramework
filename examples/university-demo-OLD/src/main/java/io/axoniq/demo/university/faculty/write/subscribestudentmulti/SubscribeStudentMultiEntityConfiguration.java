package io.axoniq.demo.university.faculty.write.subscribestudentmulti;

import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;
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

public class SubscribeStudentMultiEntityConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var courseEntity = EventSourcedEntityBuilder
                .annotatedEntity(CourseId.class, Course.class);
        var studentEntity = EventSourcedEntityBuilder
                .annotatedEntity(StudentId.class, Student.class);

        var commandHandlingModule = StatefulCommandHandlingModule
                .named("SubscribeStudentMulti")
                .entities()
                .entity(courseEntity)
                .entity(studentEntity)
                .commandHandlers()
                .commandHandlingComponent(c -> new AnnotatedCommandHandlingComponent<>(new SubscribeStudentCommandHandler(),
                                                                                       parameterResolverFactory(c)));
        return configurer.registerStatefulCommandHandlingModule(commandHandlingModule);
    }

    private static MultiParameterResolverFactory parameterResolverFactory(NewConfiguration configuration) {
        return MultiParameterResolverFactory.ordered(List.of(
                ClasspathParameterResolverFactory.forClass(SubscribeStudentMultiEntityConfiguration.class),
                // To be able to get components
                new ConfigurationParameterResolverFactory(configuration),
                // To be able to get the entity, the StateManager needs to be available.
                // When the new configuration API is there, we should have a way to resolve this
                new InjectEntityParameterResolverFactory(configuration.getComponent(StateManager.class))
        ));
    }
}
