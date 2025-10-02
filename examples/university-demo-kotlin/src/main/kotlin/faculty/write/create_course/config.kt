package io.axoniq.demo.university.faculty.write.create_course

import io.axoniq.demo.university.faculty.ids.CourseId
import org.axonframework.commandhandling.configuration.CommandHandlingModule
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer

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
