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

package org.axonframework.test.fixture;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandHandler;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * An CommandBus implementation recording all the commands that are dispatched. The recorded commands can then be used
 * to assert expectations with test cases.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class RecordingCommandBus implements CommandBus {

    private static final Logger logger = LoggerFactory.getLogger(RecordingCommandBus.class);

    private final CommandBus delegate;
    private final Map<CommandMessage, Message> recorded = new HashMap<>();

    /**
     * Creates a new {@code RecordingCommandBus} that will record all commands dispatched to the given
     * {@code delegate}.
     *
     * @param delegate The {@link CommandBus} to which commands will be dispatched.
     */
    public RecordingCommandBus(@Nonnull CommandBus delegate) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate CommandBus may not be null");
    }

    @Override
    public CompletableFuture<CommandResultMessage> dispatch(@Nonnull CommandMessage command,
                                                            @Nullable ProcessingContext processingContext) {
        logger.debug("dispatch() called for command: {} on thread {}",
                     command.payloadType().getSimpleName(),
                     Thread.currentThread().getName());
        recorded.put(command, null);
        logger.debug("dispatch() - delegating to {} on thread {}",
                     delegate.getClass().getSimpleName(),
                     Thread.currentThread().getName());
        var commandResult = delegate.dispatch(command, processingContext);
        return commandResult.thenApply(result -> {
            recorded.put(command, result);
            logger.debug("dispatch() - thenApply completed for command: {}, result: {}, total recorded: {} on thread {}",
                         command.payloadType().getSimpleName(),
                         result != null ? result.payloadType().getSimpleName() : "null",
                         recorded.size(),
                         Thread.currentThread().getName());
            return result;
        });
    }

    @Override
    public CommandBus subscribe(@Nonnull QualifiedName name, @Nonnull CommandHandler commandHandler) {
        return delegate.subscribe(name, commandHandler);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }

    /**
     * Returns map of all the {@link CommandMessage CommandMessages} dispatched, and their corresponding results.
     *
     * @return A map of all the {@link CommandMessage CommandMessages} dispatched, and their corresponding results.
     */
    public Map<CommandMessage, Message> recorded() {
        logger.debug("recorded() called, returning {} command(s) on thread {}",
                     recorded.size(),
                     Thread.currentThread().getName());
        return Map.copyOf(recorded);
    }

    /**
     * Returns the commands that have been dispatched to this {@link CommandBus}.
     *
     * @return The commands that have been dispatched to this {@link CommandBus}
     */
    public List<CommandMessage> recordedCommands() {
        logger.debug("recordedCommands() called, returning {} command(s): {} on thread {}",
                     recorded.size(),
                     recorded.keySet().stream().map(c -> c.payloadType().getSimpleName()).toList(),
                     Thread.currentThread().getName());
        return List.copyOf(recorded.keySet());
    }

    /**
     * Returns the result of the given {@code command}.
     *
     * @param command The command for which the result is returned.
     * @return The result of the given {@code command}. May be {@code null} if the command has not been dispatched yet.
     */
    @Nullable
    public Message resultOf(@Nonnull CommandMessage command) {
        Objects.requireNonNull(command, "Command Message may not be null.");
        return recorded.get(command);
    }

    /**
     * Resets this recording {@link CommandBus}, by removing all recorded {@link CommandMessage CommandMessages}.
     *
     * @return This recording {@link CommandBus}, for fluent interfacing.
     */
    public RecordingCommandBus reset() {
        logger.debug("reset() called, clearing {} recorded command(s) on thread {}",
                     recorded.size(),
                     Thread.currentThread().getName());
        recorded.clear();
        return this;
    }
}
