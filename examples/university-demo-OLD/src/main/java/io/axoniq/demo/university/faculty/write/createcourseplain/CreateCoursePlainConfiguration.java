package io.axoniq.demo.university.faculty.write.createcourseplain;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.shared.ids.CourseId;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.conversion.MessageConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.Tag;

public class CreateCoursePlainConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityModule
                .declarative(CourseId.class, CreateCourseCommandHandler.State.class)
                .messagingModel((c, model) ->
                        model.entityEvolver(
                                (entity, event, context) ->
                                        entity.evolve(
                                                event.payloadAs(
                                                        CourseCreated.class,
                                                        context.component(EventConverter.class)
                                                )
                                        )
                        ).build()
                )
                .entityFactory(c -> EventSourcedEntityFactory.fromNoArgument(CreateCourseCommandHandler.State::initial))
                .criteriaResolver(c -> (id, ctx) -> EventCriteria
                        .havingTags(Tag.of(FacultyTags.COURSE_ID, id.toString()))
                        .andBeingOneOfTypes(CourseCreated.class.getName())
                ).build();

        var commandHandlingModule = CommandHandlingModule
                .named("CreateCoursePlain")
                .commandHandlers()
                .commandHandler(new QualifiedName(CreateCourse.class),
                        c -> new CreateCourseCommandHandler(c.getComponent(MessageConverter.class), c.getComponent(MessageTypeResolver.class)));

        return configurer
                .registerEntity(stateEntity)
                .registerCommandHandlingModule(commandHandlingModule);
    }

    private CreateCoursePlainConfiguration() {
        // Prevent instantiation
    }

}
