package io.axoniq.demo.university.faculty.write.renamecourse;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.CourseRenamed;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.modelling.annotation.InjectEntity;

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
        return List.of(new CourseRenamed(command.courseId(), command.name()));
    }

    @EventSourcedEntity(tagKey = FacultyTags.COURSE_ID)
    static class State {

        private boolean created = false;
        private String name;

        @EventSourcingHandler
        void evolve(CourseCreated event) {
            this.created = true;
            this.name = event.name();
        }

        @EventSourcingHandler
        void evolve(CourseRenamed event) {
            this.name = event.name();
        }
    }
}