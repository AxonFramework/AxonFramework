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

package org.axonframework.extension.reactor.messaging.queryhandling.gateway;

import jakarta.annotation.Nonnull;
import org.axonframework.extension.reactor.messaging.core.ReactiveMessageDispatchInterceptor;
import org.axonframework.extension.reactor.messaging.core.ReactiveMessageDispatchInterceptorChain;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of {@link ReactiveQueryGateway}.
 * <p>
 * Builds a recursive interceptor chain that runs inside the Reactor subscription, then delegates to the Axon Framework
 * {@link QueryGateway}.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 * @see ReactiveQueryGateway
 * @see ReactiveMessageDispatchInterceptor
 */
public class DefaultReactiveQueryGateway implements ReactiveQueryGateway {

    private final QueryGateway delegate;
    private final MessageTypeResolver messageTypeResolver;
    private final List<ReactiveMessageDispatchInterceptor<QueryMessage>> interceptors;

    /**
     * Instantiate a {@link DefaultReactiveQueryGateway} based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link DefaultReactiveQueryGateway} instance.
     */
    protected DefaultReactiveQueryGateway(Builder builder) {
        this.delegate = builder.queryGateway;
        this.messageTypeResolver = builder.messageTypeResolver;
        this.interceptors = new CopyOnWriteArrayList<>(builder.dispatchInterceptors);
    }

    /**
     * Instantiate a {@link Builder} to construct a {@link DefaultReactiveQueryGateway}.
     *
     * @return A {@link Builder} to construct a {@link DefaultReactiveQueryGateway}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Nonnull
    @Override
    public <R> Mono<R> query(@Nonnull Object query, @Nonnull Class<R> responseType) {
        return dispatchThroughChain(query)
                .flatMap(enrichedMessage ->
                        Mono.fromFuture(delegate.query(enrichedMessage, responseType))
                );
    }

    @Nonnull
    @Override
    public <R> Mono<List<R>> queryMany(@Nonnull Object query, @Nonnull Class<R> responseType) {
        return dispatchThroughChain(query)
                .flatMap(enrichedMessage ->
                        Mono.fromFuture(delegate.queryMany(enrichedMessage, responseType))
                );
    }

    @Nonnull
    @Override
    public <R> Flux<R> streamingQuery(@Nonnull Object query, @Nonnull Class<R> responseType) {
        return dispatchThroughChain(query)
                .flatMapMany(enrichedMessage ->
                        Flux.from(delegate.streamingQuery(enrichedMessage, responseType))
                );
    }

    @Nonnull
    @Override
    public <R> Flux<R> subscriptionQuery(@Nonnull Object query, @Nonnull Class<R> responseType) {
        return dispatchThroughChain(query)
                .flatMapMany(enrichedMessage ->
                        Flux.from(delegate.subscriptionQuery(enrichedMessage, responseType))
                );
    }

    @Override
    public void registerDispatchInterceptor(
            @Nonnull ReactiveMessageDispatchInterceptor<QueryMessage> interceptor
    ) {
        interceptors.add(interceptor);
    }

    private Mono<QueryMessage> dispatchThroughChain(Object query) {
        QueryMessage queryMessage;
        if (query instanceof QueryMessage qm) {
            queryMessage = qm;
        } else if (query instanceof Message m) {
            queryMessage = new GenericQueryMessage(m);
        } else {
            queryMessage = new GenericQueryMessage(messageTypeResolver.resolveOrThrow(query), query);
        }
        return buildChain().proceed(queryMessage);
    }

    private ReactiveMessageDispatchInterceptorChain<QueryMessage> buildChain() {
        ReactiveMessageDispatchInterceptorChain<QueryMessage> chain = Mono::just;
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            var interceptor = interceptors.get(i);
            var next = chain;
            chain = message -> interceptor.interceptOnDispatch(message, next);
        }
        return chain;
    }

    /**
     * Builder class to instantiate a {@link DefaultReactiveQueryGateway}.
     * <p>
     * The {@link QueryGateway} and {@link MessageTypeResolver} are <b>hard requirements</b> and as such should be
     * provided.
     */
    public static class Builder {

        private QueryGateway queryGateway;
        private MessageTypeResolver messageTypeResolver;
        private List<ReactiveMessageDispatchInterceptor<QueryMessage>> dispatchInterceptors = new ArrayList<>();

        /**
         * Sets the {@link QueryGateway} to delegate query dispatching to.
         *
         * @param queryGateway The {@link QueryGateway} to delegate to.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder queryGateway(@Nonnull QueryGateway queryGateway) {
            this.queryGateway = Objects.requireNonNull(queryGateway, "QueryGateway may not be null");
            return this;
        }

        /**
         * Sets the {@link MessageTypeResolver} used to resolve the message type from query payloads.
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
         * Sets the list of {@link ReactiveMessageDispatchInterceptor}s to be applied to queries.
         *
         * @param dispatchInterceptors The interceptors to register.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder dispatchInterceptors(
                @Nonnull List<ReactiveMessageDispatchInterceptor<QueryMessage>> dispatchInterceptors
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
            Objects.requireNonNull(queryGateway, "The QueryGateway is a hard requirement and should be provided");
            Objects.requireNonNull(
                    messageTypeResolver, "The MessageTypeResolver is a hard requirement and should be provided"
            );
        }

        /**
         * Initializes a {@link DefaultReactiveQueryGateway} as specified through this Builder.
         *
         * @return A {@link DefaultReactiveQueryGateway} as specified through this Builder.
         */
        public DefaultReactiveQueryGateway build() {
            validate();
            return new DefaultReactiveQueryGateway(this);
        }
    }
}
