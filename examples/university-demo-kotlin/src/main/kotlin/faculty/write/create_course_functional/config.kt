package org.axonframework.examples.university.faculty.write.create_course_functional

import org.axonframework.examples.university._ext.functionalHandler
import org.axonframework.examples.university.shared.ids.CourseId
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.modelling.annotation.InjectEntity

/**
 * Pure function without enclosing type.
 */
@CommandHandler(commandName = "CreateCourse", payloadType = CreateCourse::class, routingKey = CreateCourse.ID)
internal fun handle(
    command: CreateCourse,
    @InjectEntity(idProperty = CreateCourse.ID) state: CreateCourseState,
    eventAppender: EventAppender
) {
    eventAppender.append(state.decide(command))
}

fun EventSourcingConfigurer.registerCreateCourseFunctional() = apply {
    registerEntity(
        EventSourcedEntityModule.autodetected(
            CourseId::class.java,
            CreateCourseState::class.java
        )
    )
    registerCommandHandlingModule(
        CommandHandlingModule
            .named("CreateCourse")
            .commandHandlers()
            .functionalHandler(
                ::handle,
            )
    )
}
