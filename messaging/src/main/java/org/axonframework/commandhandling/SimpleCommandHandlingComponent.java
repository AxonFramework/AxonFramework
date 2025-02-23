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
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A simple implementation of the {@link CommandHandlingComponent} interface, allowing for easy registration of
 * {@link CommandHandler}s and {@link CommandHandlingComponent}s. It also supports the registration of
 * {@link MessageHandlerInterceptor}s.
 *
 * @since 5.0.0
 * @author Allard Buijze
 * @author Gerard Klijs
 * @author Milan Savic
 * @author Mitchell Herrijgers
 * @author Sara Pellegrini
 * @author Steven van Beelen
 */
public class SimpleCommandHandlingComponent implements
        CommandHandlingComponent,
        CommandHandlerRegistry<SimpleCommandHandlingComponent>,
        CommandHandlerInterceptorSupportingComponent<SimpleCommandHandlingComponent>,
        DescribableComponent {

    private final String name;
    private final Map<QualifiedName, CommandHandler> commandHandlers = new HashMap<>();
    private final Set<CommandHandlingComponent> subComponents = new HashSet<>();
    private final List<MessageHandlerInterceptor<? super CommandMessage<?>>> interceptors = new CopyOnWriteArrayList<>();

    public static SimpleCommandHandlingComponent forComponent(String name) {
        return new SimpleCommandHandlingComponent(name);
    }

    private SimpleCommandHandlingComponent(String name) {
        this.name = name;
    }

    @Override
    public SimpleCommandHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                    @Nonnull CommandHandler commandHandler) {
        if (commandHandlers.containsKey(name)) {
            // TODO: Duplicate handler resolver?
            throw new IllegalArgumentException("CommandHandlerRegistry already contains a handler for " + name);
        }
        commandHandlers.put(name, commandHandler);
        return this;
    }

    @Override
    public SimpleCommandHandlingComponent subscribe(@Nonnull CommandHandlingComponent commandHandlingComponent) {
        subComponents.add(commandHandlingComponent);
        return this;
    }

    @Nonnull
    @Override
    public MessageStream.Single<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                          @Nonnull ProcessingContext context) {
        QualifiedName name = command.type().qualifiedName();
        // TODO #3103 - add interceptor knowledge
        Optional<CommandHandlingComponent> optionalSubHandler = subComponents
                .stream()
                .filter(subComponent ->
                                subComponent.supportedCommands().contains(name)
                )
                .findFirst();

        if (optionalSubHandler.isPresent()) {
            return invokeWithInterceptors(command,
                                          context,
                                          optionalSubHandler.get(),
                                          new CopyOnWriteArrayList<>(interceptors));
        }


        if (commandHandlers.containsKey(name)) {
            return invokeWithInterceptors(command,
                                          context,
                                          commandHandlers.get(name),
                                          new CopyOnWriteArrayList<>(interceptors));
        }
        return MessageStream.failed(new NoHandlerForCommandException(
                "No handler was subscribed for command with qualified name[%s] on component [%s]".formatted(
                        name.fullName(),
                        this.getClass().getName()))
        );
    }

    private MessageStream<? extends CommandResultMessage<?>> invokeWithInterceptors(@Nonnull CommandMessage<?> command,
                                                                                    @Nonnull ProcessingContext context,
                                                                                    @Nonnull CommandHandler commandHandler,
                                                                                    List<MessageHandlerInterceptor<? super CommandMessage<?>>> remainingInterceptors
    ) {
        if (remainingInterceptors.isEmpty()) {
            return commandHandler.handle(command, context);
        }
        MessageHandlerInterceptor<? super CommandMessage<?>> interceptor = remainingInterceptors.getFirst();
        List<MessageHandlerInterceptor<? super CommandMessage<?>>> remaining = remainingInterceptors.subList(1,
                                                                                                             remainingInterceptors.size());
        // TODO: Why is this cast necessary?
        return (MessageStream<? extends CommandResultMessage<?>>)
                interceptor.interceptOnHandle(command, context, () -> invokeWithInterceptors(command, context, commandHandler, remaining));
    }

    @Override
    public SimpleCommandHandlingComponent self() {
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

    @Override
    public SimpleCommandHandlingComponent registerInterceptor(
            MessageHandlerInterceptor<? super CommandMessage<?>> interceptor) {
        interceptors.add(interceptor);
        return this;
    }
}
