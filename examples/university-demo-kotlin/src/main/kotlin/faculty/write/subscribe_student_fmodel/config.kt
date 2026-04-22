/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.examples.university.faculty.write.subscribe_student_fmodel

import org.axonframework.examples.university.faculty.events.CourseCreated
import org.axonframework.examples.university.faculty.events.FacultyEvent
import org.axonframework.examples.university.faculty.events.StudentEnrolledInFaculty
import org.axonframework.examples.university.faculty.events.StudentSubscribedToCourse
import org.axonframework.examples.university.shared.ids.SubscriptionId
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.core.ClassBasedMessageTypeResolver
import org.axonframework.messaging.eventstreaming.EventCriteria
import org.axonframework.modelling.annotation.InjectEntity

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

    @EventSourcingHandler
    fun evolve(event: CourseCreated) = copy(state = this.state.evolve(event))

    @EventSourcingHandler
    fun evolve(event: StudentEnrolledInFaculty) = copy(state = this.state.evolve(event))

    @EventSourcingHandler
    fun evolve(event: StudentSubscribedToCourse) = copy(state = this.state.evolve(event))

    @EventSourcingHandler
    fun evolve(event: FacultyEvent): StateEntity = copy(state = this.state.evolve(event))
}

internal class SubscribeStudentToCourseFModelCommandHandler {
    @CommandHandler
    internal fun handle(
        command: SubscribeStudentToCourse,
        @InjectEntity stateEntity: StateEntity,
        eventAppender: EventAppender
    ) {
        eventAppender.append(stateEntity.state.decide(command))
    }
}

fun EventSourcingConfigurer.registerSubscribeStudentToCourseFModel() = apply {
    registerEntity(
        EventSourcedEntityModule.autodetected(
            SubscriptionId::class.java,
            StateEntity::class.java
        )
    )
    registerCommandHandlingModule(
        CommandHandlingModule
            .named("SubscribeStudentToCourseFModel")
            .commandHandlers()
            .autodetectedCommandHandlingComponent { SubscribeStudentToCourseFModelCommandHandler() }
    )
}
