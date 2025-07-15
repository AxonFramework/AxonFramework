
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
import org.axonframework.commandhandling.SimpleCommandHandlingComponent;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.MessageHandlerInterceptorMemberChain;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.serialization.Converter;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Adapter that turns any {@link @CommandHandler} annotated bean into a {@link CommandHandlingComponent} implementation.
 * Each annotated method is subscribed as a {@link org.axonframework.commandhandling.CommandHandler} at the
 * {@link CommandHandlingComponent} for the command type specified by the parameter of that method.
 *
 * @param <T> The target type of this command handling component.
 * @author Allard Buijze
 * @since 0.5.0
 */
public class AnnotatedCommandHandlingComponent<T> implements CommandHandlingComponent {

    private final T target;
    private final AnnotatedHandlerInspector<T> model;
    private final MessageTypeResolver messageTypeResolver;
    private final Converter converter;
    private final SimpleCommandHandlingComponent handlingComponent;

    /**
     * Wraps the given {@code annotatedCommandHandler}, allowing it to be subscribed to a {@link CommandBus} as a
     * {@link CommandHandlingComponent}.
     *
     * @param annotatedCommandHandler The object containing the {@link CommandHandler} annotated methods.
     * @param converter                The converter to use for converting the payload of the command to the type
     *                                 expected by the handler method.
     */
    public AnnotatedCommandHandlingComponent(@Nonnull T annotatedCommandHandler, @Nonnull Converter converter) {
        this(annotatedCommandHandler,
             ClasspathParameterResolverFactory.forClass(annotatedCommandHandler.getClass()),
             converter);
    }

    /**
     * Wraps the given {@code annotatedCommandHandler}, allowing it to be subscribed to a {@link CommandBus} as a
     * {@link CommandHandlingComponent}.
     *
     * @param annotatedCommandHandler  The object containing the {@link CommandHandler} annotated methods.
     * @param parameterResolverFactory The strategy for resolving handler method parameter values.
     * @param converter                The converter to use for converting the payload of the command to the type
     *                                  expected by the handler method.
     */
    public AnnotatedCommandHandlingComponent(@Nonnull T annotatedCommandHandler,
                                             @Nonnull ParameterResolverFactory parameterResolverFactory,
                                             @Nonnull Converter converter) {
        this(annotatedCommandHandler,
             parameterResolverFactory,
             ClasspathHandlerDefinition.forClass(annotatedCommandHandler.getClass()),
             new ClassBasedMessageTypeResolver(),
             converter);
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
     * @param converter                The converter to use for converting the payload of the command to the type
     *                                  expected by the handler method.
     */
    @SuppressWarnings("unchecked")
    public AnnotatedCommandHandlingComponent(@Nonnull T annotatedCommandHandler,
                                             @Nonnull ParameterResolverFactory parameterResolverFactory,
                                             @Nonnull HandlerDefinition handlerDefinition,
                                             @Nonnull MessageTypeResolver messageTypeResolver,
                                             @Nonnull Converter converter) {
        this.handlingComponent = SimpleCommandHandlingComponent.create(
                "AnnotationCommandHandlerAdapter[%s]".formatted(annotatedCommandHandler.getClass().getName())
        );
        this.target = requireNonNull(annotatedCommandHandler, "The Annotated Command Handler may not be null.");
        this.converter = requireNonNull(converter, "The Converter may not be null.");
        this.model = AnnotatedHandlerInspector.inspectType((Class<T>) annotatedCommandHandler.getClass(),
                                                           parameterResolverFactory,
                                                           handlerDefinition);
        this.messageTypeResolver = requireNonNull(messageTypeResolver, "The MessageTypeResolver may not be null.");

        initializeHandlersBasedOnModel();
    }

    private void initializeHandlersBasedOnModel() {
        model.getAllHandlers().forEach(
                (modelClass, handlers) ->
                        handlers.stream()
                                .filter(h -> h.canHandleMessageType(CommandMessage.class))
                                .forEach(this::registerHandler));
    }

    private void registerHandler(MessageHandlingMember<? super T> handler) {
        Class<?> payloadType = handler.payloadType();
        QualifiedName qualifiedName = handler.unwrap(CommandMessageHandlingMember.class)
                                             .map(CommandMessageHandlingMember::commandName)
                                             .map(QualifiedName::new)
                                             .orElseGet(() -> new QualifiedName(payloadType));

        MessageHandlerInterceptorMemberChain<T> interceptorChain = model.chainedInterceptor(target.getClass());
        handlingComponent.subscribe(qualifiedName, (command, ctx) -> {
            var convertedCommand = command.getPayload().getClass().equals(payloadType)
                    ? command
                    : command.withConvertedPayload(p -> converter.convert(p, payloadType));
            return interceptorChain.handle(convertedCommand, ctx, target, handler)
                            .mapMessage(this::asCommandResultMessage)
                            .first()
                            .cast();
        });
    }

    @Nonnull
    @Override
    public MessageStream.Single<CommandResultMessage<?>> handle(
            @Nonnull CommandMessage<?> command,
            @Nonnull ProcessingContext processingContext
    ) {
        return handlingComponent.handle(command, processingContext);
    }

    @SuppressWarnings("unchecked")
    private <R> CommandResultMessage<R> asCommandResultMessage(@Nullable Object commandResult) {
        if (commandResult instanceof CommandResultMessage) {
            return (CommandResultMessage<R>) commandResult;
        } else if (commandResult instanceof Message) {
            Message<R> commandResultMessage = (Message<R>) commandResult;
            return new GenericCommandResultMessage<>(commandResultMessage);
        }
        MessageType type = messageTypeResolver.resolveOrThrow(ObjectUtils.nullSafeTypeOf(commandResult));
        return new GenericCommandResultMessage<>(type, (R) commandResult);
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return Set.copyOf(handlingComponent.supportedCommands());
    }
}
