package io.axoniq.demo.university.faculty.write.enroll_student

import io.axoniq.demo.university.faculty.Faculty.Tag
import org.axonframework.commandhandling.annotations.CommandHandler
import org.axonframework.eventhandling.gateway.EventAppender
import org.axonframework.modelling.annotations.InjectEntity

class EnrollStudentCommandHandler {
  @CommandHandler
  fun handle(command: EnrollStudent, @InjectEntity(idProperty = Tag.STUDENT_ID) state: EnrollStudentState, eventAppender: EventAppender) {
    eventAppender.append(state.decide(command))
  }
}
