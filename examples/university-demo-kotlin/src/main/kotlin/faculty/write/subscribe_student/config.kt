package io.axoniq.demo.university.faculty.write.subscribe_student

import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse
import io.axoniq.demo.university.shared.ids.SubscriptionId
import org.axonframework.commandhandling.annotations.CommandHandler
import org.axonframework.commandhandling.configuration.CommandHandlingModule
import org.axonframework.eventhandling.gateway.EventAppender
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.modelling.annotations.InjectEntity

class SubscribeStudentToCourseCommandHandler {
  companion object {
    const val MAX_COURSES_PER_STUDENT = 3
  }

  @CommandHandler
  fun handle(cmd: SubscribeStudentToCourse, @InjectEntity state: SubscribeStudentToCourseState, eventAppender: EventAppender) {
    check(state.studentId != null) { "Student with given id never enrolled the faculty" }
    check(state.capacity >= state.studentsInCourse) { "Course is fully booked" }
    check(state.courseForStudent <= MAX_COURSES_PER_STUDENT) { "Student subscribed to too many courses" }
    check(!state.alreadySubscribed) { "Student already subscribed to this course" }
    check(state.courseId != null) { "Course with given id does not exist" }

    eventAppender.append(StudentSubscribedToCourse(cmd.studentId, cmd.courseId))
  }
}

fun EventSourcingConfigurer.registerSubscribeStudentToCourse() = apply {
  registerEntity(
    EventSourcedEntityModule.annotated(
      SubscriptionId::class.java, SubscribeStudentToCourseState::class.java
    )
  )
  registerCommandHandlingModule(
    CommandHandlingModule
      .named("SubscribeStudentToCourse")
      .commandHandlers()
      .annotatedCommandHandlingComponent { SubscribeStudentToCourseCommandHandler() }
  )
}
