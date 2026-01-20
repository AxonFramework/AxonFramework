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

package org.axonframework.messaging.commandhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.Assert;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * A simple implementation of the {@link CommandHandlingComponent} interface, allowing for easy registration of
 * {@link CommandHandler CommandHandlers} and other {@link CommandHandlingComponent CommandHandlingComponents}.
 * <p>
 * Registered subcomponents are preferred over registered command handlers when handling a command.
 *
 * @author Allard Buijze
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SimpleCommandHandlingComponent implements
        CommandHandlingComponent,
        CommandHandlerRegistry<SimpleCommandHandlingComponent> {

    private final String name;
    private final Map<QualifiedName, CommandHandler> commandHandlers = new HashMap<>();
    private final Set<CommandHandlingComponent> subComponents = new HashSet<>();

    /**
     * Instantiates a simple {@link CommandHandlingComponent} that is able to handle commands and delegate them to
     * subcomponents.
     *
     * @param name The name of the component, used for {@link DescribableComponent describing} the component.
     * @return A simple {@link CommandHandlingComponent} instance with the given {@code name}.
     */
    public static SimpleCommandHandlingComponent create(@Nonnull String name) {
        return new SimpleCommandHandlingComponent(name);
    }

    private SimpleCommandHandlingComponent(@Nonnull String name) {
        this.name = Assert.nonEmpty(name, "The name may not be null or empty.");
    }

    @Override
    public SimpleCommandHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                    @Nonnull CommandHandler commandHandler) {
        if (commandHandler instanceof CommandHandlingComponent component) {
            return subscribe(component);
        }

        CommandHandler existingHandler = commandHandlers.computeIfAbsent(name, k -> commandHandler);

        if (existingHandler != commandHandler) {
            throw new DuplicateCommandHandlerSubscriptionException(name, existingHandler, commandHandler);
        }

        return this;
    }

    @Override
    public SimpleCommandHandlingComponent subscribe(@Nonnull CommandHandlingComponent commandHandlingComponent) {
        subComponents.add(commandHandlingComponent);
        return this;
    }

    @Nonnull
    @Override
    public MessageStream.Single<CommandResultMessage> handle(@Nonnull CommandMessage command,
                                                             @Nonnull ProcessingContext context) {
        QualifiedName qualifiedName = requireNonNull(command, "The command message cannot be null.")
                .type()
                .qualifiedName();
        Optional<CommandHandlingComponent> optionalSubHandler =
                subComponents.stream()
                             .filter(subComponent -> subComponent.supportedCommands().contains(qualifiedName))
                             .findFirst();

        if (optionalSubHandler.isPresent()) {
            try {
                return optionalSubHandler.get().handle(command, context);
            } catch (Throwable e) {
                return MessageStream.failed(e);
            }
        }

        if (commandHandlers.containsKey(qualifiedName)) {
            try {
                return commandHandlers.get(qualifiedName).handle(command, context);
            } catch (Throwable e) {
                return MessageStream.failed(e);
            }
        }

        String message = "No handler was subscribed for command with qualified name [%s] on component [%s]. Registered handlers: [%s]"
                .formatted(qualifiedName.fullName(), this.getClass().getName(), supportedCommands());
        return MessageStream.failed(new NoHandlerForCommandException(message));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("name", name);
        descriptor.describeProperty("commandHandlers", commandHandlers);
        descriptor.describeProperty("subComponents", subComponents);
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        var combinedNames = new HashSet<>(commandHandlers.keySet());
        subComponents.forEach(subComponent -> combinedNames.addAll(subComponent.supportedCommands()));
        return combinedNames;
    }
}
