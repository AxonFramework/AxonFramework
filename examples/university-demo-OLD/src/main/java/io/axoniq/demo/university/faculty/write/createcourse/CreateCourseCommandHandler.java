package io.axoniq.demo.university.faculty.write.createcourse;

import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.shared.slices.write.CommandResult;
import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.StateManager;
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
                .thenAccept(events -> eventSink.publish(context, toMessages(events))) // todo: how to create stream from CompletableFuture<Void>
                .thenApply(r -> new GenericCommandResultMessage<>(new MessageType(CommandResult.class),
                                                                  new CommandResult(payload.courseId().raw())));
        return MessageStream.fromFuture(decideFuture);
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

    public record State(boolean created) {

        public static State initial() {
            return new State(false);
        }

        public State apply(CourseCreated event) {
            return new State(true);
        }
    }
}
