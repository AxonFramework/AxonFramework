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
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlerRegistry;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.SimpleCommandHandlingComponent;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Set;

/**
 * TODO This should be regarded as a playground object to verify the API. Feel free to remove, adjust, or replicate this class to your needs.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class CommandModelComponent
        implements CommandHandlingComponent, EventHandlingComponent, CommandHandlerRegistry<CommandModelComponent> {

    private final SimpleCommandHandlingComponent commandComponent;
    private final SimpleEventHandlingComponent eventComponent;

    public CommandModelComponent() {
        this.commandComponent = new SimpleCommandHandlingComponent();
        this.eventComponent = new SimpleEventHandlingComponent();
    }

    public CommandModelComponent subscribe(@Nonnull QualifiedName name, @Nonnull CommandHandler commandHandler) {
        commandComponent.subscribe(name, commandHandler);
        return this;
    }

    @Override
    public CommandModelComponent self() {
        return this;
    }

    @Override
    public CommandModelComponent subscribe(@Nonnull QualifiedName name, @Nonnull EventHandler eventHandler) {
        eventComponent.subscribe(name, eventHandler);
        return this;
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return commandComponent.supportedCommands();
    }

    @Override
    public Set<QualifiedName> supportedEvents() {
        return eventComponent.supportedEvents();
    }

    @Nonnull
    @Override
    public MessageStream<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                   @Nonnull ProcessingContext context) {
        return commandComponent.handle(command, context);
    }

    @Nonnull
    @Override
    public MessageStream<NoMessage> handle(@Nonnull EventMessage<?> event,
                                           @Nonnull ProcessingContext context) {
        return eventComponent.handle(event, context);
    }
}
