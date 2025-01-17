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
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleCommandHandlingComponent implements CommandHandlingComponent {

    private final ConcurrentHashMap<QualifiedName, CommandHandler> commandHandlers;

    public SimpleCommandHandlingComponent() {
        this.commandHandlers = new ConcurrentHashMap<>();
    }

    @Nonnull
    @Override
    public MessageStream<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
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
        return handler.handle(command, context);
    }

    @Override
    public SimpleCommandHandlingComponent subscribe(@Nonnull Set<QualifiedName> names,
                                                    @Nonnull CommandHandler handler) {
        names.forEach(name -> commandHandlers.put(name, Objects.requireNonNull(handler, "TODO")));
        return this;
    }

    @Override
    public SimpleCommandHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                    @Nonnull CommandHandler messageHandler) {
        return subscribe(Set.of(name), messageHandler);
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return Set.copyOf(commandHandlers.keySet());
    }
}
