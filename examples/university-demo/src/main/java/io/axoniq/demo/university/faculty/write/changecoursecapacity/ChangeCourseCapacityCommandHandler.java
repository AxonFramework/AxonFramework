package io.axoniq.demo.university.faculty.write.changecoursecapacity;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.CourseCapacityChanged;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import org.axonframework.commandhandling.annotations.CommandHandler;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.annotations.EventSourcingHandler;
import org.axonframework.eventsourcing.annotations.EventSourcedEntity;
import org.axonframework.eventsourcing.annotations.reflection.EntityCreator;
import org.axonframework.modelling.annotations.InjectEntity;

import java.util.List;

class ChangeCourseCapacityCommandHandler {

    @CommandHandler
    void handle(
            ChangeCourseCapacity command,
            @InjectEntity State state,
            EventAppender eventAppender
    ) {
        var events = decide(command, state);
        eventAppender.append(events);
    }

    private List<CourseCapacityChanged> decide(ChangeCourseCapacity command, State state) {
        if (!state.created) {
            throw new IllegalStateException("Course with given id does not exist");
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