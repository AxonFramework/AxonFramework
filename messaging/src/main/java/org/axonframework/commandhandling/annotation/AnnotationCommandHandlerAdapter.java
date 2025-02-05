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
import org.axonframework.commandhandling.CommandHandlerRegistry;
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
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.axonframework.common.BuilderUtils.assertNonNull;

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
     * Wraps the given {@code annotatedCommandHandler}, allowing it to be subscribed to a Command Bus.
     *
     * @param annotatedCommandHandler The object containing the @CommandHandler annotated methods
     */
    public AnnotationCommandHandlerAdapter(T annotatedCommandHandler) {
        this(annotatedCommandHandler, ClasspathParameterResolverFactory.forClass(annotatedCommandHandler.getClass()));
    }

    /**
     * Wraps the given {@code annotatedCommandHandler}, allowing it to be subscribed to a Command Bus.
     *
     * @param annotatedCommandHandler  The object containing the @CommandHandler annotated methods
     * @param parameterResolverFactory The strategy for resolving handler method parameter values
     */
    public AnnotationCommandHandlerAdapter(T annotatedCommandHandler,
                                           ParameterResolverFactory parameterResolverFactory) {
        this(annotatedCommandHandler,
             parameterResolverFactory,
             ClasspathHandlerDefinition.forClass(annotatedCommandHandler.getClass()),
             new ClassBasedMessageTypeResolver());
    }

    /**
     * Wraps the given {@code annotatedCommandHandler}, allowing it to be subscribed to a Command Bus.
     *
     * @param annotatedCommandHandler  The object containing the @CommandHandler annotated methods
     * @param parameterResolverFactory The strategy for resolving handler method parameter values
     * @param handlerDefinition        The handler definition used to create concrete handlers
     * @param messageTypeResolver      The {@link MessageTypeResolver} resolving the
     *                                 {@link org.axonframework.messaging.QualifiedName names} for
     *                                 {@link org.axonframework.commandhandling.CommandMessage CommandMessages}
     */
    @SuppressWarnings("unchecked")
    public AnnotationCommandHandlerAdapter(T annotatedCommandHandler,
                                           ParameterResolverFactory parameterResolverFactory,
                                           HandlerDefinition handlerDefinition,
                                           MessageTypeResolver messageTypeResolver) {
        assertNonNull(annotatedCommandHandler, "The Annotated Command Handler may not be null");
        assertNonNull(messageTypeResolver, "The Message Name Resolver may not be null");
        this.model = AnnotatedHandlerInspector.inspectType((Class<T>) annotatedCommandHandler.getClass(),
                                                           parameterResolverFactory,
                                                           handlerDefinition);

        this.target = annotatedCommandHandler;
        this.messageTypeResolver = messageTypeResolver;
    }

    @Override
    public CommandHandlerRegistry subscribe(@Nonnull QualifiedName name,
                                            @Nonnull org.axonframework.commandhandling.CommandHandler commandHandler) {
        throw new UnsupportedOperationException(
                "This Command Handling Component implementation does not YET support direct command handler registration."
        );
    }

    /**
     * Subscribe this command handler to the given {@code commandBus}. The command handler will be subscribed for each
     * of the supported commands.
     *
     * @param commandBus The command bus instance to subscribe to
     * @return A handle that can be used to unsubscribe
     */
    public Registration subscribe(CommandBus commandBus) {
//        Collection<Registration> subscriptions =
        supportedCommands().stream()
                           .forEach(supportedCommand -> commandBus.subscribe(supportedCommand, this));
//                                   .collect(Collectors.toCollection(ArrayDeque::new));
//        return () -> subscriptions.stream().map(Registration::cancel).reduce(Boolean::logicalOr).orElse(false);
        return () -> true;
    }

    @Override
    public MessageStream<CommandResultMessage<Object>> handle(@Nonnull CommandMessage<?> command,
                                                              @Nonnull ProcessingContext processingContext) {
        return model.getHandlers(target.getClass())
                    .filter(ch -> ch.canHandle(command, processingContext))
                    .findFirst()
                    .map(handler -> handler.handle(command, processingContext, target)
                                           .mapMessage(this::asCommandResultMessage))
                    .orElseGet(() -> MessageStream.failed(new NoHandlerForCommandException(command)));
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

    public boolean canHandle(CommandMessage<?> message) {
        return model.getAllHandlers()
                    .values()
                    .stream()
                    .flatMap(Collection::stream)
                    .anyMatch(h -> h.canHandle(message, null));
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return model.getAllHandlers()
                    .values()
                    .stream()
                    .flatMap(Collection::stream)
                    .map(ch -> ch.unwrap(CommandMessageHandlingMember.class).orElse(null))
                    .filter(Objects::nonNull)
                    .map(CommandMessageHandlingMember::commandName)
                    .map(QualifiedName::new)
                    .collect(Collectors.toSet());
    }
}
