package io.axoniq.demo.university.faculty.write.createcourse;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.modelling.annotation.InjectEntity;

import java.util.List;

class CreateCourseCommandHandler {

    @CommandHandler
    void handle(
            CreateCourse command,
            @InjectEntity(idProperty = FacultyTags.COURSE_ID) State state,
            EventAppender eventAppender
    ) {
        var events = decide(command, state);
        eventAppender.append(events);
    }

    private List<CourseCreated> decide(CreateCourse command, State state) {
        if (state.created) {
            return List.of();
        }
        return List.of(new CourseCreated(command.courseId(), command.name(), command.capacity()));
    }

    @EventSourcedEntity(tagKey = FacultyTags.COURSE_ID)
    static final class State {

        private boolean created;

        @EntityCreator
        private State() {
            this.created = false;
        }

        @EventSourcingHandler
        private State apply(CourseCreated event) {
            this.created = true;
            return this;
        }
    }
}
