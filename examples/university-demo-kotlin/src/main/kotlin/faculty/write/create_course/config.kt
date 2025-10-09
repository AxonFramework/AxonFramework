package io.axoniq.demo.university.faculty.write.create_course

import io.axoniq.demo.university.shared.ids.CourseId
import org.axonframework.commandhandling.annotations.CommandHandler
import org.axonframework.commandhandling.configuration.CommandHandlingModule
import org.axonframework.eventhandling.gateway.EventAppender
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.modelling.annotations.InjectEntity

class CreateCourseCommandHandler {

  @CommandHandler
  fun handle(command: CreateCourse, @InjectEntity(idProperty = CreateCourse.ID) state: CreateCourseState, eventAppender: EventAppender) {
    eventAppender.append(state.decide(command))
  }

}

fun EventSourcingConfigurer.registerCreateCourse() = apply {
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
      .annotatedCommandHandlingComponent { CreateCourseCommandHandler() })
}
