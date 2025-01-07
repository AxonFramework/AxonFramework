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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Set;

/**
 * TODO Should reside in the query module
 * TODO Should have an interface.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class CommandModelComponent implements MessageHandlingComponent<Message<?>, Message<?>> {

    private final CommandHandlingComponent commandComponent;
    private final EventHandlingComponent eventComponent;

    public CommandModelComponent() {
        this.commandComponent = new CommandHandlingComponent();
        this.eventComponent = new EventHandlingComponent();
    }

    @Nonnull
    @Override
    public MessageStream<? extends Message<?>> handle(@Nonnull Message<?> message, @Nonnull ProcessingContext context) {
        return switch (message) {
            case CommandMessage<?> command -> commandComponent.handle(command, context);
            case EventMessage<?> event -> eventComponent.handle(event, context);
            default -> throw new IllegalArgumentException(
                    "Cannot handle message of type " + message.getClass()
                            + ". Only CommandMessages and EventMessages are supported."
            );
        };
    }

    @Override
    public <H extends MessageHandler<M, R>, M extends Message<?>, R extends Message<?>> CommandModelComponent subscribe(
            @Nonnull Set<QualifiedName> names,
            @Nonnull H messageHandler
    ) {
        if (messageHandler instanceof CommandHandler commandHandler) {
            commandComponent.registerCommandHandler(names, commandHandler);
            return this;
        }
        if (messageHandler instanceof EventHandler eventHandler) {
            eventComponent.registerEventHandler(names, eventHandler);
            return this;
        }
        throw new IllegalArgumentException("Cannot register query handlers on a command model component");
    }

    @Override
    public <H extends MessageHandler<M, R>, M extends Message<?>, R extends Message<?>> CommandModelComponent subscribe(
            @Nonnull QualifiedName name,
            @Nonnull H messageHandler
    ) {
        return subscribe(Set.of(name), messageHandler);
    }

    public <C extends CommandHandler> CommandModelComponent registerCommandHandler(@Nonnull QualifiedName messageType,
                                                                                   @Nonnull C commandHandler) {
        commandComponent.registerCommandHandler(messageType, commandHandler);
        return this;
    }

    public <E extends EventHandler> CommandModelComponent registerEventHandler(@Nonnull QualifiedName messageType,
                                                                               @Nonnull E eventHandler) {
        eventComponent.registerEventHandler(messageType, eventHandler);
        return this;
    }

    @Override
    public Set<QualifiedName> supportedMessages() {
        Set<QualifiedName> supportedMessage = commandComponent.supportedMessages();
        supportedMessage.addAll(eventComponent.supportedMessages());
        return supportedMessage;
    }
}
