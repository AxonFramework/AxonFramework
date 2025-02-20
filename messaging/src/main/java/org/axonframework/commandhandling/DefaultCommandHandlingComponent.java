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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DefaultCommandHandlingComponent implements
        CommandHandlingComponent,
        HierarchicalCommandHandlerRegistry<DefaultCommandHandlingComponent>,
        DescribableComponent {

    private final String name;
    private final Map<QualifiedName, CommandHandler> commandHandlers = new HashMap<>();
    private final Set<CommandHandlingComponent> subComponents = new HashSet<>();

    public static DefaultCommandHandlingComponent forComponent(String name) {
        return new DefaultCommandHandlingComponent(name);
    }

    private DefaultCommandHandlingComponent(String name) {
        this.name = name;
    }

    @Override
    public DefaultCommandHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                     @Nonnull CommandHandler commandHandler) {
        if (commandHandlers.containsKey(name)) {
            // TODO: Duplicate handler resolver?
            throw new IllegalArgumentException("CommandHandlerRegistry already contains a handler for " + name);
        }
        commandHandlers.put(name, commandHandler);
        return this;
    }

    @Override
    public DefaultCommandHandlingComponent subscribeChildHandlingComponent(
            @Nonnull CommandHandlingComponent commandHandlingComponent) {
        subComponents.add(commandHandlingComponent);
        return this;
    }

    @Nonnull
    @Override
    public MessageStream<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                   @Nonnull ProcessingContext context) {
        QualifiedName name = command.type().qualifiedName();
        Optional<CommandHandlingComponent> optionalSubHandler = subComponents
                .stream()
                .filter(subComponent ->
                                subComponent.supportedCommands().contains(name)
                )
                .findFirst();

        if (optionalSubHandler.isPresent()) {
            return optionalSubHandler.get().handle(command, context);
        }


        if (commandHandlers.containsKey(name)) {
            return commandHandlers.get(name).handle(command, context);
        }
        return MessageStream.failed(new NoHandlerForCommandException(
                "No handler was subscribed for command with qualified name[%s] on component [%s]".formatted(
                        name.fullName(),
                        this.getClass().getName()))
        );
    }

    @Override
    public DefaultCommandHandlingComponent self() {
        return this;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("name", name);
        descriptor.describeProperty("commandHandlers", commandHandlers);
        subComponents.forEach(descriptor::describeWrapperOf);
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        var combinedNames = new HashSet<>(commandHandlers.keySet());
        subComponents.forEach(subComponent -> combinedNames.addAll(subComponent.supportedCommands()));
        return combinedNames;
    }
}
