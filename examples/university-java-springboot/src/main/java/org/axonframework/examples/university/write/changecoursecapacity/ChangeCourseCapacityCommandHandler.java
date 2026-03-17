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

package org.axonframework.examples.university.write.changecoursecapacity;

import jakarta.validation.Valid;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.examples.university.event.CourseCapacityChanged;
import org.axonframework.examples.university.event.CourseCreated;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.examples.university.shared.FacultyTags;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Component
@Validated
class ChangeCourseCapacityCommandHandler {

    @CommandHandler
    void handle(
            @Valid ChangeCourseCapacity command,
            @InjectEntity State state,
            EventAppender eventAppender
    ) {
        var events = decide(command, state);
        eventAppender.append(events);
    }

    private List<CourseCapacityChanged> decide(ChangeCourseCapacity command, State state) {
        if (!state.created) {
            throw new IllegalStateException("Course with given courseId does not exist");
        }
        if (command.capacity() == state.capacity) {
            return List.of();
        }
        return List.of(new CourseCapacityChanged(command.courseId(), command.capacity()));
    }

    @EventSourced(tagKey = FacultyTags.COURSE_ID, idType = CourseId.class)
    static class State {

        private boolean created = false;
        private int capacity;

        @EntityCreator
        public State() {
        }

        @EventSourcingHandler
        void evolve(CourseCreated event) {
            this.created = true;
            this.capacity = event.capacity();
        }

        @EventSourcingHandler
        void evolve(CourseCapacityChanged event) {
            this.capacity = event.capacity();
        }
    }
}