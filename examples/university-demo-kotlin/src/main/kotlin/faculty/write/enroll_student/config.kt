package io.axoniq.demo.university.faculty.write.enroll_student

import io.axoniq.demo.university.faculty.FacultyTags
import io.axoniq.demo.university.shared.ids.StudentId
import org.axonframework.commandhandling.annotations.CommandHandler
import org.axonframework.commandhandling.configuration.CommandHandlingModule
import org.axonframework.eventhandling.gateway.EventAppender
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.modelling.annotations.InjectEntity

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
