package io.axoniq.demo.university.faculty.write.subscribe_student_fmodel

import io.axoniq.demo.university.faculty.events.CourseCreated
import io.axoniq.demo.university.faculty.events.FacultyEvent
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse
import io.axoniq.demo.university.shared.ids.SubscriptionId
import org.axonframework.commandhandling.annotations.CommandHandler
import org.axonframework.commandhandling.configuration.CommandHandlingModule
import org.axonframework.eventhandling.gateway.EventAppender
import org.axonframework.eventsourcing.annotations.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotations.EventSourcedEntity
import org.axonframework.eventsourcing.annotations.EventSourcingHandler
import org.axonframework.eventsourcing.annotations.reflection.EntityCreator
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.eventstreaming.EventCriteria
import org.axonframework.messaging.ClassBasedMessageTypeResolver
import org.axonframework.modelling.annotations.InjectEntity

@EventSourcedEntity
internal data class StateEntity @EntityCreator constructor(
  val state: State = State.InitialState
) {

  companion object {

    val CLASS_MESSAGE_TYPE_RESOLVER = ClassBasedMessageTypeResolver()

    @JvmStatic
    @EventCriteriaBuilder
    fun resolveCriteria(id: SubscriptionId): EventCriteria = EventCriteria.either(
      EventCriteria
        .havingTags(id.courseIdTag())
        .andBeingOneOfTypes(
          CLASS_MESSAGE_TYPE_RESOLVER,
          CourseCreated::class.java,
          StudentSubscribedToCourse::class.java,
        ),
      EventCriteria
        .havingTags(id.studentTag())
        .andBeingOneOfTypes(
          CLASS_MESSAGE_TYPE_RESOLVER,
          StudentEnrolledInFaculty::class.java,
          StudentSubscribedToCourse::class.java,
        )
    )
  }

  // TODO -> can't we do better?
  @EventSourcingHandler
  fun evolve(event: CourseCreated) = copy(this.state.evolve(event))

  @EventSourcingHandler
  fun evolve(event: StudentEnrolledInFaculty) = copy(this.state.evolve(event))

  @EventSourcingHandler
  fun evolve(event: StudentSubscribedToCourse) = copy(this.state.evolve(event))

  @EventSourcingHandler
  fun evolve(event: FacultyEvent) : StateEntity {
    return copy(this.state.evolve(event))
  }
}

internal class SubscribeStudentToCourseFModelCommandHandler {
  @CommandHandler
  internal fun handle(command: SubscribeStudentToCourse, @InjectEntity stateEntity: StateEntity, eventAppender: EventAppender) {
    eventAppender.append(stateEntity.state.decide(command))
  }
}

fun EventSourcingConfigurer.registerSubscribeStudentToCourseFModel() = apply {
  registerEntity(
    EventSourcedEntityModule.annotated(
      SubscriptionId::class.java,
      StateEntity::class.java
    )
  )
  registerCommandHandlingModule(
    CommandHandlingModule
      .named("SubscribeStudentToCourseFModel")
      .commandHandlers()
      .annotatedCommandHandlingComponent { SubscribeStudentToCourseFModelCommandHandler() }
  )
}
