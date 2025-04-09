package io.axoniq.demo.university.faculty.write.renamecourse;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.CourseRenamed;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.annotation.InjectEntity;

import java.util.List;
import java.util.stream.Collectors;

class RenameCourseCommandHandler {

    @CommandHandler
    void handle(
            RenameCourse command,
            @InjectEntity State state,
            EventSink eventSink,
            ProcessingContext processingContext
    ) {
        var events = decide(command, state);
        eventSink.publish(processingContext, toMessages(events));
    }

    private List<CourseRenamed> decide(RenameCourse command, State state) {
        if (!state.created) {
            throw new IllegalStateException("Course with given id does not exist");
        }
        if (command.name().equals(state.name)) {
            return List.of();
        }
        return List.of(new CourseRenamed(command.courseId().raw(), command.name()));
    }

    private static List<EventMessage<?>> toMessages(List<CourseRenamed> events) {
        return events.stream()
                     .map(RenameCourseCommandHandler::toMessage)
                     .collect(Collectors.toList());
    }

    private static EventMessage<?> toMessage(Object payload) {
        return new GenericEventMessage<>(
                new MessageType(payload.getClass()),
                payload
        );
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