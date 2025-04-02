package io.axoniq.demo.university.faculty.write.createcourse;

import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.shared.slices.write.CommandResult;
import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.command.EntityIdResolver;
import org.axonframework.modelling.command.StatefulCommandHandler;

import java.util.List;
import java.util.stream.Collectors;

class CreateCourseCommandHandler implements StatefulCommandHandler {

    private final EventSink eventSink;

    CreateCourseCommandHandler(EventSink eventSink) {
        this.eventSink = eventSink;
    }

    @Override
    public @Nonnull MessageStream.Single<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                                   @Nonnull StateManager state,
                                                                                   @Nonnull ProcessingContext context) {
        var payload = (CreateCourse) command.getPayload();
        var decideFuture = state
                .loadEntity(State.class, payload.courseId(), context)
                .thenApply(entity -> decide(payload, entity))
                .thenAccept(events -> eventSink.publish(context, toMessages(events)))
                .thenApply(r -> new GenericCommandResultMessage<>(new MessageType(CommandResult.class),
                                                                  new CommandResult(payload.courseId().raw())));
        return MessageStream.fromFuture(decideFuture);
    }

    private List<CourseCreated> decide(CreateCourse command, State state) {
        if (state.courseId == null) {
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

    @EventSourcedEntity
    public static class State {

        private String courseId;

        public State() {
        }

        @EventSourcingHandler
        public void evolve(CourseCreated event) {
            this.courseId = event.courseId();
        }
    }

    public static class CourseIdResolver implements EntityIdResolver<CourseId> {

        @Nonnull
        @Override
        public CourseId resolve(@Nonnull Message<?> message, @Nonnull ProcessingContext context) {
            var id = resolveOrNull(message);
            if (id == null) {
                throw new IllegalArgumentException("Cannot resolve course courseId from the command");
            }
            return id;
        }

        private static CourseId resolveOrNull(Message<?> message) {
            var payload = message.getPayload();
            return payload instanceof CreateCourse createCourse
                    ? createCourse.courseId()
                    : null;
        }
    }
}
