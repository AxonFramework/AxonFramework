package io.axoniq.demo.university.faculty.write.createcourseplain;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.shared.ids.CourseId;
import org.axonframework.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.eventhandling.conversion.EventConverter;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.Tag;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.conversion.MessageConverter;

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
