package io.axoniq.demo.university.faculty.write.enroll_student

import io.axoniq.demo.university.faculty.FacultyTags
import io.axoniq.demo.university.shared.ids.StudentId
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.modelling.annotation.InjectEntity

class EnrollStudentCommandHandler {
  @CommandHandler
  internal fun handle(command: EnrollStudent, @InjectEntity(idProperty = EnrollStudent.ID_PROP) state: EnrollStudentState, eventAppender: EventAppender) {
    eventAppender.append(state.decide(command))
  }
}

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
