package io.axoniq.demo.university.faculty.write.create_course

import io.axoniq.demo.university.faculty.Faculty.Tag.COURSE_ID
import org.axonframework.commandhandling.annotations.CommandHandler
import org.axonframework.eventhandling.gateway.EventAppender
import org.axonframework.modelling.annotations.InjectEntity

class CreateCourseCommandHandler {

  @CommandHandler
  fun handle(command: CreateCourse, @InjectEntity(idProperty = COURSE_ID) state: CreateCourseState, eventAppender: EventAppender) {
    eventAppender.append(state.decide(command))
  }

}
