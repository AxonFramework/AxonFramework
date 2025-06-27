package io.axoniq.demo.university.faculty.write.createcourseplain;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.shared.ids.CourseId;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.Tag;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;
import org.axonframework.serialization.Converter;

public class CreateCoursePlainConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityModule
                .declarative(CourseId.class, CreateCourseCommandHandler.State.class)
                .messagingModel((c, model) ->
                        model.entityEvolver(
                                (entity, event, context) ->
                                        entity.evolve(
                                                event.withConvertedPayload(
                                                        p -> c.getComponent(Converter.class).convert(p, CourseCreated.class)
                                                ).getPayload()
                                        )
                        ).build()
                )
                .entityFactory(c -> EventSourcedEntityFactory.fromNoArgument(CreateCourseCommandHandler.State::initial))
                .criteriaResolver(c -> (id, ctx) -> EventCriteria
                        .havingTags(Tag.of(FacultyTags.COURSE_ID, id.toString()))
                        .andBeingOneOfTypes(CourseCreated.class.getName())
                ).build();

        var commandHandlingModule = StatefulCommandHandlingModule
                .named("CreateCoursePlain")
                .entities()
                .entity(stateEntity)
                .commandHandlers()
                .commandHandler(new QualifiedName(CreateCourse.class),
                        c -> new CreateCourseCommandHandler(c.getComponent(EventSink.class), c.getComponent(MessageTypeResolver.class)));

        return configurer.registerStatefulCommandHandlingModule(commandHandlingModule);
    }

    private CreateCoursePlainConfiguration() {
        // Prevent instantiation
    }

}
