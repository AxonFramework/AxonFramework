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

package org.axonframework.messaging.annotation2;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class AnnotatedCommandHandlingComponent<T> implements CommandHandlingComponent {

    public static final Class<org.axonframework.commandhandling.annotation.CommandHandler> ANNOTATION_CLASS =
            org.axonframework.commandhandling.annotation.CommandHandler.class;
    private final T instance;
    private final Map<QualifiedName, CommandHandler> commandHandlers = new HashMap<>();

    public AnnotatedCommandHandlingComponent(T instance) {
        this.instance = instance;
        inspectComponent(instance);
    }

    private void inspectComponent(T instance) {
        ReflectionUtils.methodsOf(instance.getClass())
                       .forEach(this::registerPotentialCommandHandler);
    }

    private void registerPotentialCommandHandler(Method method) {
        AnnotationUtils.findAnnotationAttributes(method, ANNOTATION_CLASS)
                       .ifPresent(attributes -> {
                           String commandName = requireNonNull(attributes.get("commandName").toString());
                           QualifiedName qualifiedName = new QualifiedName(commandName);
                           commandHandlers.put(qualifiedName, new MethodCommandHandler(instance, method));
                       });
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return commandHandlers.keySet();
    }

    @Nonnull
    @Override
    public MessageStream.Single<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                   @Nonnull ProcessingContext context) {
        var handler = commandHandlers.get(command.type().qualifiedName());
        if (handler == null) {
            return MessageStream.failed(new NoHandlerForCommandException(
                    "No handler was subscribed for command with qualified name[%s] on component [%s]".formatted(
                            command.type().qualifiedName().fullName(),
                            instance.getClass().getName()))
            );
        }
        return handler.handle(command, context);
    }

    static class MethodCommandHandler implements CommandHandler {

        private final Object target;
        private final Method method;

        public MethodCommandHandler(Object target, Method method) {
            this.target = target;
            this.method = method;
        }

        @Override
        public MessageStream.Single<? extends CommandResultMessage<?>> handle(CommandMessage<?> commandMessage,
                                                                       ProcessingContext processingContext) {
            try {
                Object result = method.invoke(target, commandMessage);
                if (result instanceof MessageStream.Single<?> resultStream) {
                    return resultStream.mapMessage(r -> {
                        if (r instanceof CommandResultMessage<?> cr) {
                            return cr;
                        }
                        throw new IllegalArgumentException("Expected CommandResultMessage but got: " + r);
                    });
                }
                return (MessageStream.Single<? extends CommandResultMessage<?>>) result;
            } catch (Exception e) {
                return MessageStream.failed(e);
            }
        }
    }
}
