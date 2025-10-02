package io.axoniq.demo.university.faculty.write.enroll_student

import io.axoniq.demo.university.shared.ids.StudentId
import org.axonframework.commandhandling.configuration.CommandHandlingModule
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer

fun EventSourcingConfigurer.registerEnrollStudent() = apply {
  registerEntity(
    EventSourcedEntityModule.annotated(
      StudentId::class.java,
      EnrollStudentState::class.java
    )
  )
  registerCommandHandlingModule(
    CommandHandlingModule
      .named("EnrollStudent")
      .commandHandlers()
      .annotatedCommandHandlingComponent { EnrollStudentCommandHandler() })
}
