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

package org.axonframework.extension.reactor.messaging.commandhandling.gateway;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptor;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptorChain;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * Default implementation of {@link ReactorCommandGateway}.
 * <p>
 * Builds a recursive interceptor chain (same pattern as Axon Framework's
 * {@link org.axonframework.messaging.commandhandling.interception.InterceptingCommandBus InterceptingCommandBus}). The
 * entire chain runs inside the Reactor subscription, so interceptors have access to Reactor context such as
 * {@code ReactiveSecurityContextHolder}.
 * <p>
 * Flow:
 * <ol>
 *   <li>If the command is already a {@link CommandMessage}, pass it through as-is; if it's a plain
 *       {@link Message}, wrap it in {@link GenericCommandMessage}; otherwise create a new
 *       {@link GenericCommandMessage} from the payload using {@link MessageTypeResolver}</li>
 *   <li>Build chain: last link returns the message, each preceding link calls its interceptor</li>
 *   <li>Execute {@code chain.proceed(commandMessage)} — runs inside the Reactor subscription</li>
 *   <li>Dispatch the enriched message to the delegate {@link CommandGateway}</li>
 * </ol>
 *
 * @author Milan Savic
 * @author Theo Emanuelsson
 * @since 4.4.2
 * @see ReactorCommandGateway
 * @see ReactorMessageDispatchInterceptor
 */
public class DefaultReactorCommandGateway implements ReactorCommandGateway {

    private final CommandGateway delegate;
    private final MessageTypeResolver messageTypeResolver;
    private final List<ReactorMessageDispatchInterceptor<? super CommandMessage>> interceptors;

    /**
     * Instantiate a {@link DefaultReactorCommandGateway}.
     *
     * @param commandGateway       the {@link CommandGateway} to delegate command dispatching to
     * @param messageTypeResolver  the {@link MessageTypeResolver} for resolving message types
     * @param dispatchInterceptors the list of {@link ReactorMessageDispatchInterceptor}s to apply to commands
     */
    public DefaultReactorCommandGateway(
            @NonNull CommandGateway commandGateway,
            @NonNull MessageTypeResolver messageTypeResolver,
            @NonNull List<ReactorMessageDispatchInterceptor<? super CommandMessage>> dispatchInterceptors
    ) {
        this.delegate = Objects.requireNonNull(commandGateway, "CommandGateway may not be null");
        this.messageTypeResolver = Objects.requireNonNull(messageTypeResolver, "MessageTypeResolver may not be null");
        this.interceptors = List.copyOf(
                Objects.requireNonNull(dispatchInterceptors, "Dispatch interceptors may not be null")
        );
    }

    /**
     * Instantiate a {@link DefaultReactorCommandGateway} without dispatch interceptors.
     *
     * @param commandGateway      the {@link CommandGateway} to delegate command dispatching to
     * @param messageTypeResolver the {@link MessageTypeResolver} for resolving message types
     */
    public DefaultReactorCommandGateway(
            @NonNull CommandGateway commandGateway,
            @NonNull MessageTypeResolver messageTypeResolver
    ) {
        this(commandGateway, messageTypeResolver, List.of());
    }

    @NonNull
    @Override
    public <R> Mono<R> send(@NonNull Object command, @NonNull Class<R> resultType,
                            @Nullable ProcessingContext context) {
        return dispatchThroughChain(command, context)
                .flatMap(enrichedMessage ->
                        Mono.fromFuture(delegate.send(enrichedMessage, context).resultAs(resultType))
                );
    }

    @NonNull
    @Override
    public Mono<Void> send(@NonNull Object command, @Nullable ProcessingContext context) {
        return dispatchThroughChain(command, context)
                .flatMap(enrichedMessage ->
                        Mono.fromFuture(delegate.send(enrichedMessage, context).getResultMessage())
                )
                .then();
    }

    @SuppressWarnings("unchecked")
    private Mono<CommandMessage> dispatchThroughChain(Object command, ProcessingContext context) {
        CommandMessage commandMessage;
        if (command instanceof CommandMessage cm) {
            commandMessage = cm;
        } else if (command instanceof Message m) {
            commandMessage = new GenericCommandMessage(m);
        } else {
            commandMessage = new GenericCommandMessage(messageTypeResolver.resolveOrThrow(command), command);
        }
        return (Mono<CommandMessage>) buildChain().proceed(commandMessage, context);
    }

    private ReactorMessageDispatchInterceptorChain<CommandMessage> buildChain() {
        ReactorMessageDispatchInterceptorChain<CommandMessage> chain = (message, ctx) -> Mono.just(message);
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            // Safe cast: each interceptor in the list can handle CommandMessage,
            // because they accept "CommandMessage or a supertype of CommandMessage".
            //noinspection rawtypes,unchecked
            var interceptor = (ReactorMessageDispatchInterceptor) interceptors.get(i);
            var next = chain;
            //noinspection unchecked
            chain = (message, ctx) -> interceptor.interceptOnDispatch(message, ctx, next);
        }
        return chain;
    }
}
