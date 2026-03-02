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

import jakarta.annotation.Nonnull;
import org.axonframework.extension.reactor.messaging.core.ReactiveMessageDispatchInterceptor;
import org.axonframework.extension.reactor.messaging.core.ReactiveMessageDispatchInterceptorChain;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageTypeResolver;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of {@link ReactiveCommandGateway}.
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
 * @author Theo Emanuelsson
 * @since 5.1.0
 * @see ReactiveCommandGateway
 * @see ReactiveMessageDispatchInterceptor
 */
public class DefaultReactiveCommandGateway implements ReactiveCommandGateway {

    private final CommandGateway delegate;
    private final MessageTypeResolver messageTypeResolver;
    private final List<ReactiveMessageDispatchInterceptor<CommandMessage>> interceptors;

    /**
     * Instantiate a {@link DefaultReactiveCommandGateway} based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link DefaultReactiveCommandGateway} instance.
     */
    protected DefaultReactiveCommandGateway(Builder builder) {
        this.delegate = builder.commandGateway;
        this.messageTypeResolver = builder.messageTypeResolver;
        this.interceptors = new CopyOnWriteArrayList<>(builder.dispatchInterceptors);
    }

    /**
     * Instantiate a {@link Builder} to construct a {@link DefaultReactiveCommandGateway}.
     *
     * @return A {@link Builder} to construct a {@link DefaultReactiveCommandGateway}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Nonnull
    @Override
    public <R> Mono<R> send(@Nonnull Object command, @Nonnull Class<R> resultType) {
        return dispatchThroughChain(command)
                .flatMap(enrichedMessage ->
                        Mono.fromFuture(delegate.send(enrichedMessage).resultAs(resultType))
                );
    }

    @Nonnull
    @Override
    public Mono<Void> send(@Nonnull Object command) {
        return dispatchThroughChain(command)
                .flatMap(enrichedMessage ->
                        Mono.fromFuture(delegate.send(enrichedMessage).getResultMessage())
                )
                .then();
    }

    @Override
    public void registerDispatchInterceptor(
            @Nonnull ReactiveMessageDispatchInterceptor<CommandMessage> interceptor
    ) {
        interceptors.add(interceptor);
    }

    private Mono<CommandMessage> dispatchThroughChain(Object command) {
        CommandMessage commandMessage;
        if (command instanceof CommandMessage cm) {
            commandMessage = cm;
        } else if (command instanceof Message m) {
            commandMessage = new GenericCommandMessage(m);
        } else {
            commandMessage = new GenericCommandMessage(messageTypeResolver.resolveOrThrow(command), command);
        }
        return buildChain().proceed(commandMessage);
    }

    private ReactiveMessageDispatchInterceptorChain<CommandMessage> buildChain() {
        ReactiveMessageDispatchInterceptorChain<CommandMessage> chain = Mono::just;
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            var interceptor = interceptors.get(i);
            var next = chain;
            chain = message -> interceptor.interceptOnDispatch(message, next);
        }
        return chain;
    }

    /**
     * Builder class to instantiate a {@link DefaultReactiveCommandGateway}.
     * <p>
     * The {@link CommandGateway} and {@link MessageTypeResolver} are <b>hard requirements</b> and as such should be
     * provided.
     */
    public static class Builder {

        private CommandGateway commandGateway;
        private MessageTypeResolver messageTypeResolver;
        private List<ReactiveMessageDispatchInterceptor<CommandMessage>> dispatchInterceptors = new ArrayList<>();

        /**
         * Sets the {@link CommandGateway} to delegate command dispatching to.
         *
         * @param commandGateway The {@link CommandGateway} to delegate to.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder commandGateway(@Nonnull CommandGateway commandGateway) {
            this.commandGateway = Objects.requireNonNull(commandGateway, "CommandGateway may not be null");
            return this;
        }

        /**
         * Sets the {@link MessageTypeResolver} used to resolve the message type from command payloads.
         *
         * @param messageTypeResolver The {@link MessageTypeResolver} to use.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder messageTypeResolver(@Nonnull MessageTypeResolver messageTypeResolver) {
            this.messageTypeResolver = Objects.requireNonNull(
                    messageTypeResolver, "MessageTypeResolver may not be null"
            );
            return this;
        }

        /**
         * Sets the list of {@link ReactiveMessageDispatchInterceptor}s to be applied to commands.
         *
         * @param dispatchInterceptors The interceptors to register.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder dispatchInterceptors(
                @Nonnull List<ReactiveMessageDispatchInterceptor<CommandMessage>> dispatchInterceptors
        ) {
            this.dispatchInterceptors = Objects.requireNonNull(
                    dispatchInterceptors, "Dispatch interceptors may not be null"
            );
            return this;
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         */
        protected void validate() {
            Objects.requireNonNull(commandGateway, "The CommandGateway is a hard requirement and should be provided");
            Objects.requireNonNull(
                    messageTypeResolver, "The MessageTypeResolver is a hard requirement and should be provided"
            );
        }

        /**
         * Initializes a {@link DefaultReactiveCommandGateway} as specified through this Builder.
         *
         * @return A {@link DefaultReactiveCommandGateway} as specified through this Builder.
         */
        public DefaultReactiveCommandGateway build() {
            validate();
            return new DefaultReactiveCommandGateway(this);
        }
    }
}
