package io.axoniq.demo.university.faculty.write

import io.axoniq.demo.university.faculty.Faculty.Event.CourseCreated
import io.axoniq.demo.university.faculty.Faculty.Tag.COURSE_ID
import io.axoniq.demo.university.shared.CourseId
import org.axonframework.commandhandling.annotation.CommandHandler
import org.axonframework.commandhandling.configuration.CommandHandlingModule
import org.axonframework.eventhandling.gateway.EventAppender
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.modelling.annotation.InjectEntity

/**
 * Configure slice CreateCourse
 */
fun EventSourcingConfigurer.registerCreateCourse() = apply {
  registerEntity(
    EventSourcedEntityModule.annotated(
      CourseId::class.java,
      CreateCourseCommandHandler.State::class.java
    )
  )
  registerCommandHandlingModule(
    CommandHandlingModule
      .named("CreateCourse")
      .commandHandlers()
      .annotatedCommandHandlingComponent { CreateCourseCommandHandler() })
}

/**
 * Command to create a course.
 */
data class CreateCourse(
  val courseId: CourseId,
  val name: String,
  val capacity: Int
)

/**
 * Command handler for [CreateCourse].
 */
class CreateCourseCommandHandler {

  @CommandHandler
  fun handle(command: CreateCourse, @InjectEntity(idProperty = COURSE_ID) state: State, eventAppender: EventAppender) {
    eventAppender.append(state.decide(command))
  }

  @EventSourcedEntity(tagKey = COURSE_ID)
  class State @EntityCreator constructor() {

    private var created: Boolean = false

    fun decide(command: CreateCourse): List<Any> {
      if (created) {
        return listOf()
      }
      return listOf(CourseCreated(command.courseId, command.name, command.capacity))
    }

    @EventSourcingHandler
    fun apply(event: CourseCreated): State {
      this.created = true
      return this
    }
  }
}
