
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

package org.axonframework.commandhandling.annotations;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.SimpleCommandHandlingComponent;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotations.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotations.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotations.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotations.HandlerDefinition;
import org.axonframework.messaging.annotations.MessageHandlingMember;
import org.axonframework.messaging.annotations.ParameterResolverFactory;
import org.axonframework.messaging.conversion.MessageConverter;
import org.axonframework.messaging.interceptors.annotations.MessageHandlerInterceptorMemberChain;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Adapter that turns classes with {@link CommandHandler} annotated methods into a {@link CommandHandlingComponent}.
 * <p>
 * Each annotated method is subscribed as a {@link org.axonframework.commandhandling.CommandHandler} at the
 * {@link CommandHandlingComponent} for the command name specified by the parameter of that method.
 *
 * @param <T> The target type of this command handling component.
 * @author Allard Buijze
 * @since 0.5.0
 */
public class AnnotatedCommandHandlingComponent<T> implements CommandHandlingComponent {

    private final SimpleCommandHandlingComponent handlingComponent;
    private final T target;
    private final AnnotatedHandlerInspector<T> model;
    private final MessageTypeResolver messageTypeResolver;
    private final MessageConverter converter;

    /**
     * Wraps the given {@code annotatedCommandHandler}, allowing it to be subscribed to a {@link CommandBus} as a
     * {@link CommandHandlingComponent}.
     *
     * @param annotatedCommandHandler The object containing the {@link CommandHandler} annotated methods.
     * @param converter               The converter to use for converting the payload of the command to the type
     *                                expected by the handler method.
     */
    public AnnotatedCommandHandlingComponent(@Nonnull T annotatedCommandHandler,
                                             @Nonnull MessageConverter converter) {
        this(annotatedCommandHandler,
             ClasspathParameterResolverFactory.forClass(annotatedCommandHandler.getClass()),
             converter);
    }

    /**
     * Wraps the given {@code annotatedCommandHandler}, allowing it to be subscribed to a {@link CommandBus} as a
     * {@link CommandHandlingComponent}.
     *
     * @param annotatedCommandHandler  The object containing the {@link CommandHandler} annotated methods.
     * @param parameterResolverFactory The parameter resolver factory to resolve handler parameters with.
     * @param converter                The converter to use for converting the payload of the command to the type
     *                                 expected by the handler method.
     */
    public AnnotatedCommandHandlingComponent(@Nonnull T annotatedCommandHandler,
                                             @Nonnull ParameterResolverFactory parameterResolverFactory,
                                             @Nonnull MessageConverter converter) {
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
     * @param parameterResolverFactory The parameter resolver factory to resolve handler parameters with.
     * @param handlerDefinition        The handler definition used to create concrete handlers.
     * @param messageTypeResolver      The {@link MessageTypeResolver} resolving the
     *                                 {@link org.axonframework.messaging.QualifiedName names} for
     *                                 {@link org.axonframework.commandhandling.CommandMessage CommandMessages}.
     * @param converter                The converter to use for converting the payload of the command to the type
     *                                 expected by the handler method.
     */
    public AnnotatedCommandHandlingComponent(@Nonnull T annotatedCommandHandler,
                                             @Nonnull ParameterResolverFactory parameterResolverFactory,
                                             @Nonnull HandlerDefinition handlerDefinition,
                                             @Nonnull MessageTypeResolver messageTypeResolver,
                                             @Nonnull MessageConverter converter) {
        this.handlingComponent = SimpleCommandHandlingComponent.create(
                "AnnotatedCommandHandlingComponent[%s]".formatted(annotatedCommandHandler.getClass().getName())
        );
        this.target = requireNonNull(annotatedCommandHandler, "The Annotated Command Handler may not be null.");
        //noinspection unchecked
        this.model = AnnotatedHandlerInspector.inspectType((Class<T>) annotatedCommandHandler.getClass(),
                                                           parameterResolverFactory,
                                                           handlerDefinition);
        this.messageTypeResolver = requireNonNull(messageTypeResolver, "The MessageTypeResolver may not be null.");
        this.converter = requireNonNull(converter, "The Converter may not be null.");

        initializeHandlersBasedOnModel();
    }

    private void initializeHandlersBasedOnModel() {
        model.getAllHandlers().forEach(
                (modelClass, handlers) ->
                        handlers.stream()
                                .filter(h -> h.canHandleMessageType(CommandMessage.class))
                                .forEach(this::registerHandler)
        );
    }

    private void registerHandler(MessageHandlingMember<? super T> handler) {
        Class<?> payloadType = handler.payloadType();
        QualifiedName qualifiedName = handler.unwrap(CommandHandlingMember.class)
                                             .map(CommandHandlingMember::commandName)
                                             // Only use names as is that not match the fully qualified class name.
                                             .filter(name -> !name.equals(payloadType.getName()))
                                             .map(QualifiedName::new)
                                             .orElseGet(() -> messageTypeResolver.resolve(payloadType)
                                                                                 .orElse(new MessageType(payloadType))
                                                                                 .qualifiedName());

        MessageHandlerInterceptorMemberChain<T> interceptorChain = model.chainedInterceptor(target.getClass());
        handlingComponent.subscribe(
                qualifiedName,
                (command, ctx) -> interceptorChain.handle(
                                                          command.withConvertedPayload(payloadType, converter),
                                                          ctx,
                                                          target,
                                                          handler
                                                  )
                                                  .mapMessage(this::asCommandResultMessage)
                                                  .first()
                                                  .cast()
        );
    }

    private CommandResultMessage asCommandResultMessage(@Nonnull Message commandResult) {
        return commandResult instanceof CommandResultMessage
                ? (CommandResultMessage) commandResult
                : new GenericCommandResultMessage(commandResult);
    }

    @Nonnull
    @Override
    public MessageStream.Single<CommandResultMessage> handle(@Nonnull CommandMessage command,
                                                             @Nonnull ProcessingContext processingContext) {
        return handlingComponent.handle(command, processingContext);
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return Set.copyOf(handlingComponent.supportedCommands());
    }
}
