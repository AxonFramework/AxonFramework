package io.axoniq.demo.university.faculty.write.subscribe_student

import io.axoniq.demo.university.shared.ids.SubscriptionId
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.modelling.annotation.InjectEntity

class SubscribeStudentToCourseCommandHandler {
  @CommandHandler
  internal fun handle(command: SubscribeStudentToCourse, @InjectEntity state: State, eventAppender: EventAppender) {
    eventAppender.append(state.decide(command))
  }
}

fun EventSourcingConfigurer.registerSubscribeStudentToCourse() = apply {
  registerEntity(
    EventSourcedEntityModule.annotated(
      SubscriptionId::class.java, State::class.java
    )
  )
  registerCommandHandlingModule(
    CommandHandlingModule
      .named("SubscribeStudentToCourse")
      .commandHandlers()
      .annotatedCommandHandlingComponent { SubscribeStudentToCourseCommandHandler() }
  )
}
