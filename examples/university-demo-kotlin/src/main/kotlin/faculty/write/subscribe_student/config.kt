package io.axoniq.demo.university.faculty.write.subscribe_student

import io.axoniq.demo.university.shared.ids.SubscriptionId
import org.axonframework.commandhandling.annotations.CommandHandler
import org.axonframework.commandhandling.configuration.CommandHandlingModule
import org.axonframework.eventhandling.gateway.EventAppender
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.modelling.annotations.InjectEntity

class SubscribeStudentToCourseCommandHandler {
  @CommandHandler
  fun handle(command: SubscribeStudentToCourse, @InjectEntity state: SubscribeStudentToCourseState, eventAppender: EventAppender) {
    eventAppender.append(state.decide(command))
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
