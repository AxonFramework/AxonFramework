package io.axoniq.demo.university.faculty.write.subscribe_student_polymorph

import io.axoniq.demo.university.shared.ids.SubscriptionId
import org.axonframework.commandhandling.annotations.CommandHandler
import org.axonframework.commandhandling.configuration.CommandHandlingModule
import org.axonframework.eventhandling.gateway.EventAppender
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.modelling.annotations.InjectEntity

class SubscribeStudentToCoursePolymorphCommandHandler {
  @CommandHandler
  fun handle(command: SubscribeStudentToCourse, @InjectEntity state: State, eventAppender: EventAppender) {
    eventAppender.append(state.decide(command))
  }
}

fun EventSourcingConfigurer.registerSubscribeStudentToCoursePolymorph() = apply {
  registerEntity(
    EventSourcedEntityModule.annotated(
      SubscriptionId::class.java, State::class.java
    )
  )
  registerCommandHandlingModule(
    CommandHandlingModule
      .named("SubscribeStudentToCoursePolymorph")
      .commandHandlers()
      .annotatedCommandHandlingComponent { SubscribeStudentToCoursePolymorphCommandHandler() }
  )
}
