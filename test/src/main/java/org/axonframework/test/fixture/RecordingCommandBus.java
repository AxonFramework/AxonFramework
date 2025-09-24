/*
 * Copyright (c) 2010-2025. Axon Framework
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
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.common.annotations.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

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

    private final CommandBus delegate;
    private final Map<CommandMessage, Message> recorded = new HashMap<>();

    /**
     * Creates a new {@link RecordingCommandBus} that will record all commands dispatched to the given {@code delegate}.
     *
     * @param delegate The {@link CommandBus} to which commands will be dispatched.
     */
    public RecordingCommandBus(@Nonnull CommandBus delegate) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate CommandBus may not be null");
    }

    @Override
    public CompletableFuture<CommandResultMessage<?>> dispatch(@Nonnull CommandMessage command,
                                                               @Nullable ProcessingContext processingContext) {
        recorded.put(command, null);
        var commandResult = delegate.dispatch(command, processingContext);
        commandResult.thenApply(result -> {
            recorded.put(command, result);
            return result;
        });
        return commandResult;
    }

    @Override
    public CommandBus subscribe(@Nonnull QualifiedName name, @Nonnull CommandHandler commandHandler) {
        return delegate.subscribe(name, commandHandler);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }

    public Map<CommandMessage, Message> recorded() {
        return Map.copyOf(recorded);
    }

    public List<CommandMessage> recordedCommands() {
        return List.copyOf(recorded.keySet());
    }

    public Message resultOf(CommandMessage command) {
        return recorded.get(command);
    }

    public RecordingCommandBus reset() {
        recorded.clear();
        return this;
    }
}
