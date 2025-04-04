package io.axoniq.demo.university.faculty.write.createcourse;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.annotation.InjectEntity;

import java.util.List;
import java.util.stream.Collectors;

class CreateCourseCommandHandler {

    @CommandHandler
    public void handle(
            CreateCourse command,
            @InjectEntity State state,
            EventSink eventSink,
            ProcessingContext processingContext
    ) {
        var events = decide(command, state);
        eventSink.publish(processingContext, toMessages(events));
    }

    private List<CourseCreated> decide(CreateCourse command, State state) {
        if (state.created) {
            return List.of();
        }
        return List.of(new CourseCreated(command.courseId().raw(), command.name(), command.capacity()));
    }

    private static List<EventMessage<?>> toMessages(List<CourseCreated> events) {
        return events.stream()
                     .map(CreateCourseCommandHandler::toMessage)
                     .collect(Collectors.toList());
    }

    private static EventMessage<?> toMessage(Object payload) {
        return new GenericEventMessage<>(
                new MessageType(payload.getClass()),
                payload
        );
    }

    @EventSourcedEntity(tagKey = FacultyTags.COURSE_ID)
    public static final class State {

        private boolean created;

        private State() {
            this.created = false;
        }

        @EventSourcingHandler
        public State apply(CourseCreated event) {
            this.created = true;
            return this;
        }
    }
}
