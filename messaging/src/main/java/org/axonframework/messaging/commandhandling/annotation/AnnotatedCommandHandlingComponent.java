
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

package org.axonframework.messaging.commandhandling.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandHandlingComponent;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.commandhandling.SimpleCommandHandlingComponent;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.core.annotation.AnnotationMessageTypeResolver;
import org.axonframework.messaging.core.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.core.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.core.annotation.HandlerDefinition;
import org.axonframework.messaging.core.annotation.MessageHandlingMember;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.core.conversion.MessageConverter;
import org.axonframework.messaging.core.interception.annotation.MessageHandlerInterceptorMemberChain;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Adapter that turns classes with {@link CommandHandler} annotated methods into a {@link CommandHandlingComponent}.
 * <p>
 * Each annotated method is subscribed as a {@link org.axonframework.messaging.commandhandling.CommandHandler} at the
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
             new AnnotationMessageTypeResolver(),
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
     *                                 {@link QualifiedName names} for
     *                                 {@link CommandMessage CommandMessages}.
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

        @SuppressWarnings("unchecked")
        Class<T> cls = (Class<T>) annotatedCommandHandler.getClass();

        this.model = AnnotatedHandlerInspector.inspectType(cls,
                                                           parameterResolverFactory,
                                                           handlerDefinition);
        this.messageTypeResolver = requireNonNull(messageTypeResolver, "The MessageTypeResolver may not be null.");
        this.converter = requireNonNull(converter, "The Converter may not be null.");

        initializeHandlersBasedOnModel();
    }

    private void initializeHandlersBasedOnModel() {
        model.getUniqueHandlers(target.getClass(), CommandMessage.class).forEach(this::registerHandler);
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
