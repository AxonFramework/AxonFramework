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

package org.axonframework.messaging.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CommandHandlingComponent implements MessageHandlingComponent<CommandMessage<?>, CommandResultMessage<?>> {

    private final ConcurrentHashMap<QualifiedName, CommandHandler> commandHandlers;

    public CommandHandlingComponent() {
        this.commandHandlers = new ConcurrentHashMap<>();
    }

    @Nonnull
    @Override
    public MessageStream<CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                         @Nonnull ProcessingContext context) {
        QualifiedName name = command.name();
        // TODO add interceptor knowledge
        CommandHandler handler = commandHandlers.get(name);
        if (handler == null) {
            // TODO this would benefit from a dedicate exception
            return MessageStream.failed(new IllegalArgumentException(
                    "No handler found for command with name [" + name + "]"
            ));
        }
        // TODO - can we do something about this cast?
        return (MessageStream<CommandResultMessage<?>>) handler.apply(command, context);
    }

    @Override
    public <H extends MessageHandler<M, R>, M extends Message<?>, R extends Message<?>> CommandHandlingComponent subscribe(
            @Nonnull Set<QualifiedName> names,
            @Nonnull H messageHandler
    ) {
        if (messageHandler instanceof EventHandler) {
            throw new UnsupportedOperationException("Cannot register event handlers on a command handling component");
        }
        if (messageHandler instanceof QueryHandler) {
            throw new UnsupportedOperationException("Cannot register query handlers on a command handling component");
        }
        names.forEach(name -> commandHandlers.put(name, (CommandHandler) messageHandler));
        return this;
    }

    @Override
    public <H extends MessageHandler<M, R>, M extends Message<?>, R extends Message<?>> CommandHandlingComponent subscribe(
            @Nonnull QualifiedName name,
            @Nonnull H messageHandler
    ) {
        return subscribe(Set.of(name), messageHandler);
    }

    /**
     * @param names
     * @param commandHandler
     * @param <C>
     * @return
     */
    public <C extends CommandHandler> CommandHandlingComponent registerCommandHandler(@Nonnull Set<QualifiedName> names,
                                                                                      @Nonnull C commandHandler) {
        return subscribe(names, commandHandler);
    }

    /**
     * @param name
     * @param commandHandler
     * @param <C>
     * @return
     */
    public <C extends CommandHandler> CommandHandlingComponent registerCommandHandler(@Nonnull QualifiedName name,
                                                                                      @Nonnull C commandHandler) {
        return registerCommandHandler(Set.of(name), commandHandler);
    }

    @Override
    public Set<QualifiedName> supportedMessages() {
        return commandHandlers.keySet();
    }
}
