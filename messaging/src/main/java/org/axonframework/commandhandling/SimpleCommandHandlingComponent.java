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

package org.axonframework.commandhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO This should be regarded as a playground object to verify the API. Feel free to remove, adjust, or replicate this class to your needs.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class SimpleCommandHandlingComponent implements CommandHandlingComponent, CommandHandlerRegistry<SimpleCommandHandlingComponent> {

    private final ConcurrentHashMap<QualifiedName, CommandHandler> commandHandlers;

    public SimpleCommandHandlingComponent() {
        this.commandHandlers = new ConcurrentHashMap<>();
    }

    @Nonnull
    @Override
    public MessageStream<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                   @Nonnull ProcessingContext context) {
        QualifiedName name = command.type().qualifiedName();
        // TODO add interceptor knowledge
        CommandHandler handler = commandHandlers.get(name);
        if (handler == null) {
            // TODO this would benefit from a dedicate exception
            return MessageStream.failed(new IllegalArgumentException(
                    "No handler found for command with name [" + name + "]"
            ));
        }
        return handler.handle(command, context);
    }

    public SimpleCommandHandlingComponent subscribe(@Nonnull Set<QualifiedName> names,
                                                    @Nonnull CommandHandler handler) {
        names.forEach(name -> commandHandlers.put(name, Objects.requireNonNull(handler, "TODO")));
        return this;
    }

    public SimpleCommandHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                    @Nonnull CommandHandler messageHandler) {
        return subscribe(Set.of(name), messageHandler);
    }

    @Override
    public SimpleCommandHandlingComponent self() {
        return this;
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return Set.copyOf(commandHandlers.keySet());
    }
}
