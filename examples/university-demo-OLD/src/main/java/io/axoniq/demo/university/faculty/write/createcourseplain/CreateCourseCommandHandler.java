package io.axoniq.demo.university.faculty.write.createcourseplain;

import io.axoniq.demo.university.faculty.Ids;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.shared.slices.write.CommandResult;
import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.CommandHandler;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.conversion.MessageConverter;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.StateManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;

class CreateCourseCommandHandler implements CommandHandler {

    private final MessageConverter messageConverter;
    private final MessageTypeResolver messageTypeResolver;

    CreateCourseCommandHandler(MessageConverter messageConverter, MessageTypeResolver messageTypeResolver) {
        this.messageConverter = messageConverter;
        this.messageTypeResolver = messageTypeResolver;
    }

    @Override
    @Nonnull
    public MessageStream.Single<CommandResultMessage> handle(
            @Nonnull CommandMessage command,
            @Nonnull ProcessingContext context
    ) {
        var eventAppender = EventAppender.forContext(context);
        var payload = command.payloadAs(CreateCourse.class, messageConverter);
        var state = context.component(StateManager.class);
        CompletableFuture<CommandResultMessage> decideFuture = state
                .loadEntity(State.class, payload.courseId(), context)
                .thenApply(entity -> decide(payload, entity))
                .thenAccept(eventAppender::append)
                .thenApply(r -> new GenericCommandResultMessage(messageTypeResolver.resolveOrThrow(CommandResult.class),
                                                                  new CommandResult(payload.courseId().toString())));
        return MessageStream.fromFuture(decideFuture);
    }

    private List<CourseCreated> decide(CreateCourse command, State state) {
        if (state.created) {
            return List.of();
        }
        return List.of(new CourseCreated(Ids.FACULTY_ID, command.courseId(), command.name(), command.capacity()));
    }

    static final class State {

        private boolean created;

        private State(boolean created) {
            this.created = created;
        }

        static State initial() {
            return new State(false);
        }

        State evolve(CourseCreated event) {
            this.created = true;
            return this;
        }
    }
}
