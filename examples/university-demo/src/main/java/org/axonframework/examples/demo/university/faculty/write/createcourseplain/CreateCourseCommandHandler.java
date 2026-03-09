/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.examples.demo.university.faculty.write.createcourseplain;

import org.axonframework.examples.demo.university.faculty.Ids;
import org.axonframework.examples.demo.university.faculty.events.CourseCreated;
import org.axonframework.examples.demo.university.shared.slices.write.CommandResult;
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
import org.jspecify.annotations.NonNull;

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
    public MessageStream.@NonNull Single<CommandResultMessage> handle(
            @NonNull CommandMessage command,
            @NonNull ProcessingContext context
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
