package io.axoniq.demo.university.faculty.write.renamecourse;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.Ids;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.CourseRenamed;
import org.axonframework.commandhandling.annotations.CommandHandler;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.annotations.EventSourcingHandler;
import org.axonframework.eventsourcing.annotations.EventSourcedEntity;
import org.axonframework.eventsourcing.annotations.reflection.EntityCreator;
import org.axonframework.modelling.annotations.InjectEntity;

import java.util.List;

class RenameCourseCommandHandler {

    @CommandHandler
    void handle(
            RenameCourse command,
            @InjectEntity State state,
            EventAppender eventAppender
    ) {
        var events = decide(command, state);
        eventAppender.append(events);
    }

    private List<CourseRenamed> decide(RenameCourse command, State state) {
        if (!state.created) {
            throw new IllegalStateException("Course with given id does not exist");
        }
        if (command.name().equals(state.name)) {
            return List.of();
        }
        return List.of(new CourseRenamed(Ids.FACULTY_ID, command.courseId(), command.name()));
    }

    @EventSourcedEntity(tagKey = FacultyTags.COURSE_ID)
    record State(
            boolean created,
            String name
    ) {

        @EntityCreator
        static State initial() {
            return new State(false, null);
        }

        @EventSourcingHandler
        State evolve(CourseCreated event) {
            return new State(true, event.name());
        }

        @EventSourcingHandler
        State evolve(CourseRenamed event) {
            return new State(this.created, event.name());
        }

    }

// Alternative State implementation based on mutable class
//    @EventSourcedEntity(tagKey = FacultyTags.COURSE_ID)
//    static class State {
//
//        private boolean created = false;
//        private String name;
//
//        @EntityCreator
//        public State() {
//        }
//
//        @EventSourcingHandler
//        void evolve(CourseCreated event) {
//            this.created = true;
//            this.name = event.name();
//        }
//
//        @EventSourcingHandler
//        void evolve(CourseRenamed event) {
//            this.name = event.name();
//        }
//    }
}