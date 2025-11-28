package org.axonframework.examples.university.write.changecoursecapacity;

import jakarta.validation.Valid;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.examples.university.event.CourseCapacityChanged;
import org.axonframework.examples.university.event.CourseCreated;
import org.axonframework.examples.university.shared.FacultyTags;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;

import java.util.List;

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

    @EventSourcedEntity(tagKey = FacultyTags.COURSE_ID)
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