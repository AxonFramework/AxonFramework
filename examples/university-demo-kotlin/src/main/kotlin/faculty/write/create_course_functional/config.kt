package io.axoniq.demo.university.faculty.write.create_course_functional

import io.axoniq.demo.university._ext.functionalHandler
import io.axoniq.demo.university.shared.ids.CourseId
import org.axonframework.commandhandling.annotations.CommandHandler
import org.axonframework.commandhandling.configuration.CommandHandlingModule
import org.axonframework.eventhandling.gateway.EventAppender
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.modelling.annotations.InjectEntity

/**
 * Pure function without enclosing type.
 */
@CommandHandler(commandName = "CreateCourse", payloadType = CreateCourse::class, routingKey = CreateCourse.ID)
internal fun handle(command: CreateCourse, @InjectEntity(idProperty = CreateCourse.ID) state: CreateCourseState, eventAppender: EventAppender) {
  eventAppender.append(state.decide(command))
}

fun EventSourcingConfigurer.registerCreateCourseFunctional() = apply {
  registerEntity(
    EventSourcedEntityModule.annotated(
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
