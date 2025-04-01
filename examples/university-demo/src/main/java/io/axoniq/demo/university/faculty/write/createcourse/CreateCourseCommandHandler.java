package io.axoniq.demo.university.faculty.write.createcourse;

import io.axoniq.demo.university.faculty.events.CourseCreated;
import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.EntityIdResolver;
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
        if (state.name.equals(command.name())) {
            return List.of();
        }
        return List.of(new CourseCreated(command.courseId(), command.name(), command.capacity()));
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

    public static class State {

        private final String courseId;
        private String name;

        public State(String courseId) {
            this.courseId = courseId;
        }

        @EventSourcingHandler
        public void evolve(CourseCreated event) {
            this.name = event.name();
        }
    }

    public static class CourseIdResolver implements EntityIdResolver<String> {

        @Nonnull
        @Override
        public String resolve(@Nonnull Message<?> message, @Nonnull ProcessingContext context) {
            var id = resolveOrNull(message);
            if (id == null) {
                throw new IllegalArgumentException("Cannot resolve course courseId from the command");
            }
            return id;
        }

        private static String resolveOrNull(Message<?> message) {
            var payload = message.getPayload();
            return payload instanceof CreateCourse createCourse
                    ? createCourse.courseId()
                    : null;
        }
    }
}
