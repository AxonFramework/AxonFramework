/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Adapter that turns any {@link CommandHandler @CommandHandler} annotated bean into a {@link MessageHandler}
 * implementation. Each annotated method is subscribed as a Command Handler at the {@link CommandBus} for the command
 * type specified by the parameter of that method.
 *
 * @author Allard Buijze
 * @see CommandHandler
 * @since 0.5
 */
public class AnnotationCommandHandlerAdapter<T> implements CommandMessageHandler {

    private final T target;
    private final AnnotatedHandlerInspector<T> model;

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
             ClasspathHandlerDefinition.forClass(annotatedCommandHandler.getClass()));
    }

    /**
     * Wraps the given {@code annotatedCommandHandler}, allowing it to be subscribed to a Command Bus.
     *
     * @param annotatedCommandHandler  The object containing the @CommandHandler annotated methods
     * @param parameterResolverFactory The strategy for resolving handler method parameter values
     * @param handlerDefinition        The handler definition used to create concrete handlers
     */
    @SuppressWarnings("unchecked")
    public AnnotationCommandHandlerAdapter(T annotatedCommandHandler,
                                           ParameterResolverFactory parameterResolverFactory,
                                           HandlerDefinition handlerDefinition) {
        assertNonNull(annotatedCommandHandler, "The Annotated Command Handler may not be null");
        this.model = AnnotatedHandlerInspector.inspectType((Class<T>) annotatedCommandHandler.getClass(),
                                                           parameterResolverFactory,
                                                           handlerDefinition);

        this.target = annotatedCommandHandler;
    }

    /**
     * Subscribe this command handler to the given {@code commandBus}. The command handler will be subscribed for each
     * of the supported commands.
     *
     * @param commandBus The command bus instance to subscribe to
     * @return A handle that can be used to unsubscribe
     */
    public Registration subscribe(CommandBus commandBus) {
        Collection<Registration> subscriptions =
                supportedCommandNames().stream()
                                       .map(supportedCommand -> commandBus.subscribe(supportedCommand, this))
                                       .collect(Collectors.toCollection(ArrayDeque::new));
        return () -> subscriptions.stream().map(Registration::cancel).reduce(Boolean::logicalOr).orElse(false);
    }

    /**
     * Invokes the {@link CommandHandler @CommandHandler} annotated method that accepts the given
     * {@link CommandMessage command}.
     *
     * @param command The command to handle.
     * @return The result of the command handling. Is {@code null} when the annotated handler has a {@code void} return
     * value.
     * @throws NoHandlerForCommandException When no handler is found for given {@code command}.
     * @throws Exception                    Any exception occurring while handling the command.
     */
    @Override
    public Object handleSync(CommandMessage<?> command) throws Exception {
        MessageHandlingMember<? super T> handler =
                model.getHandlers(target.getClass())
                     .filter(ch -> ch.canHandle(command))
                     .findFirst()
                     .orElseThrow(() -> new NoHandlerForCommandException(command));

        return model.chainedInterceptor(target.getClass())
                    .handle(command, target, handler);
    }

    @Override
    public boolean canHandle(CommandMessage<?> message) {
        return model.getAllHandlers()
                    .values()
                    .stream()
                    .flatMap(Collection::stream)
                    .anyMatch(h -> h.canHandle(message));
    }

    @Override
    public Set<String> supportedCommandNames() {
        return model.getAllHandlers()
                    .values()
                    .stream()
                    .flatMap(Collection::stream)
                    .map(ch -> ch.unwrap(CommandMessageHandlingMember.class).orElse(null))
                    .filter(Objects::nonNull)
                    .map(CommandMessageHandlingMember::commandName)
                    .collect(Collectors.toSet());
    }
}
