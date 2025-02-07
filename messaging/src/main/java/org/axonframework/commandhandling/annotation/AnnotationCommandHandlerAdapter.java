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

package org.axonframework.commandhandling.annotation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.common.ObjectUtils;
import org.axonframework.common.Registration;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Adapter that turns any {@link @CommandHandler} annotated bean into a {@link MessageHandler} implementation. Each
 * annotated method is subscribed as a Command Handler at the {@link CommandBus} for the command type specified by the
 * parameter of that method.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class AnnotationCommandHandlerAdapter<T> implements CommandHandlingComponent {

    private final T target;
    private final AnnotatedHandlerInspector<T> model;
    private final MessageTypeResolver messageTypeResolver;

    /**
     * Wraps the given {@code annotatedCommandHandler}, allowing it to be subscribed to a {@link CommandBus} as a
     * {@link CommandHandlingComponent}.
     *
     * @param annotatedCommandHandler The object containing the {@link CommandHandler} annotated methods.
     */
    public AnnotationCommandHandlerAdapter(@Nonnull T annotatedCommandHandler) {
        this(annotatedCommandHandler, ClasspathParameterResolverFactory.forClass(annotatedCommandHandler.getClass()));
    }

    /**
     * Wraps the given {@code annotatedCommandHandler}, allowing it to be subscribed to a {@link CommandBus} as a
     * {@link CommandHandlingComponent}.
     *
     * @param annotatedCommandHandler  The object containing the {@link CommandHandler} annotated methods.
     * @param parameterResolverFactory The strategy for resolving handler method parameter values.
     */
    public AnnotationCommandHandlerAdapter(@Nonnull T annotatedCommandHandler,
                                           @Nonnull ParameterResolverFactory parameterResolverFactory) {
        this(annotatedCommandHandler,
             parameterResolverFactory,
             ClasspathHandlerDefinition.forClass(annotatedCommandHandler.getClass()),
             new ClassBasedMessageTypeResolver());
    }

    /**
     * Wraps the given {@code annotatedCommandHandler}, allowing it to be subscribed to a {@link CommandBus} as a
     * {@link CommandHandlingComponent}.
     *
     * @param annotatedCommandHandler  The object containing the {@link CommandHandler} annotated methods.
     * @param parameterResolverFactory The strategy for resolving handler method parameter values.
     * @param handlerDefinition        The handler definition used to create concrete handlers.
     * @param messageTypeResolver      The {@link MessageTypeResolver} resolving the
     *                                 {@link org.axonframework.messaging.QualifiedName names} for
     *                                 {@link org.axonframework.commandhandling.CommandMessage CommandMessages}.
     */
    @SuppressWarnings("unchecked")
    public AnnotationCommandHandlerAdapter(@Nonnull T annotatedCommandHandler,
                                           @Nonnull ParameterResolverFactory parameterResolverFactory,
                                           @Nonnull HandlerDefinition handlerDefinition,
                                           @Nonnull MessageTypeResolver messageTypeResolver) {
        this.target = requireNonNull(annotatedCommandHandler, "The Annotated Command Handler may not be null");
        this.model = AnnotatedHandlerInspector.inspectType((Class<T>) annotatedCommandHandler.getClass(),
                                                           parameterResolverFactory,
                                                           handlerDefinition);
        this.messageTypeResolver = requireNonNull(messageTypeResolver, "The MessageTypeResolver may not be null");
    }

    @Override
    public AnnotationCommandHandlerAdapter<T> subscribe(@Nonnull QualifiedName name,
                                                        @Nonnull org.axonframework.commandhandling.CommandHandler commandHandler) {
        throw new UnsupportedOperationException(
                "This Command Handling Component does not support direct command handler registration."
        );
    }

    @Nonnull
    @Override
    public MessageStream<CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                         @Nonnull ProcessingContext processingContext) {
        MessageHandlingMember<? super T> handler = model.getAllHandlers()
                                                        .values()
                                                        .stream()
                                                        .flatMap(Collection::stream)
                                                        .filter(ch -> ch.canHandle(command, processingContext))
                                                        .findFirst()
                                                        .orElseThrow(() -> new NoHandlerForCommandException(command));

        return model.chainedInterceptor(target.getClass())
                    .handle(command, processingContext, target, handler)
                    .mapMessage(this::asCommandResultMessage);
    }

    public boolean canHandle(CommandMessage<?> message) {
        return model.getAllHandlers()
                    .values()
                    .stream()
                    .flatMap(Collection::stream)
                    .anyMatch(h -> h.canHandle(message, null));
    }

    @SuppressWarnings("unchecked")
    private <R> CommandResultMessage<R> asCommandResultMessage(@Nullable Object commandResult) {
        if (commandResult instanceof CommandResultMessage) {
            return (CommandResultMessage<R>) commandResult;
        } else if (commandResult instanceof Message) {
            Message<R> commandResultMessage = (Message<R>) commandResult;
            return new GenericCommandResultMessage<>(commandResultMessage);
        }
        MessageType type = messageTypeResolver.resolve(ObjectUtils.nullSafeTypeOf(commandResult));
        return new GenericCommandResultMessage<>(type, (R) commandResult);
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return model.getAllHandlers()
                    .values()
                    .stream()
                    .flatMap(Collection::stream)
                    .map(ch -> ch.unwrap(CommandMessageHandlingMember.class)
                                 .orElse(null))
                    .filter(Objects::nonNull)
                    .map(CommandMessageHandlingMember::commandName)
                    .map(QualifiedName::new)
                    .collect(Collectors.toSet());
    }
}
