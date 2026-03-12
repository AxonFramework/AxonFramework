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

import org.axonframework.common.infra.ComponentDescriptor;
import org.jspecify.annotations.Nullable;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptor;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptorChain;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * Default implementation of {@link ReactorQueryGateway}.
 * <p>
 * Builds a recursive interceptor chain that runs inside the Reactor subscription, then delegates to the Axon Framework
 * {@link QueryGateway}.
 *
 * @author Milan Savic
 * @author Theo Emanuelsson
 * @since 4.4.2
 * @see ReactorQueryGateway
 * @see ReactorMessageDispatchInterceptor
 */
public class DefaultReactorQueryGateway implements ReactorQueryGateway {

    private final QueryGateway delegate;
    private final MessageTypeResolver messageTypeResolver;
    private final ReactorMessageDispatchInterceptorChain<QueryMessage> interceptorChain;

    /**
     * Instantiate a {@link DefaultReactorQueryGateway}.
     *
     * @param queryGateway         the {@link QueryGateway} to delegate query dispatching to
     * @param messageTypeResolver  the {@link MessageTypeResolver} for resolving message types
     * @param dispatchInterceptors the list of {@link ReactorMessageDispatchInterceptor}s to apply to queries
     */
    public DefaultReactorQueryGateway(
            QueryGateway queryGateway,
            MessageTypeResolver messageTypeResolver,
            List<ReactorMessageDispatchInterceptor<? super QueryMessage>> dispatchInterceptors
    ) {
        this.delegate = Objects.requireNonNull(queryGateway, "QueryGateway may not be null");
        this.messageTypeResolver = Objects.requireNonNull(messageTypeResolver, "MessageTypeResolver may not be null");
        this.interceptorChain = buildChain(
                Objects.requireNonNull(dispatchInterceptors, "Dispatch interceptors may not be null")
        );
    }

    /**
     * Instantiate a {@link DefaultReactorQueryGateway} without dispatch interceptors.
     *
     * @param queryGateway        the {@link QueryGateway} to delegate query dispatching to
     * @param messageTypeResolver the {@link MessageTypeResolver} for resolving message types
     */
    public DefaultReactorQueryGateway(
            QueryGateway queryGateway,
            MessageTypeResolver messageTypeResolver
    ) {
        this(queryGateway, messageTypeResolver, List.of());
    }

    @Override
    public <R> Mono<R> query(Object query, Class<R> responseType,
                             @Nullable ProcessingContext context) {
        return dispatchThroughChain(query, context)
                .flatMap(enrichedMessage ->
                        Mono.fromFuture(delegate.query(enrichedMessage, responseType, context))
                );
    }

    @Override
    public <R> Mono<List<R>> queryMany(Object query, Class<R> responseType,
                                       @Nullable ProcessingContext context) {
        return dispatchThroughChain(query, context)
                .flatMap(enrichedMessage ->
                        Mono.fromFuture(delegate.queryMany(enrichedMessage, responseType, context))
                );
    }

    @Override
    public <R> Flux<R> streamingQuery(Object query, Class<R> responseType,
                                      @Nullable ProcessingContext context) {
        return dispatchThroughChain(query, context)
                .flatMapMany(enrichedMessage ->
                        Flux.from(delegate.streamingQuery(enrichedMessage, responseType, context))
                );
    }

    @Override
    public <R> Flux<R> subscriptionQuery(Object query, Class<R> responseType,
                                         @Nullable ProcessingContext context) {
        return dispatchThroughChain(query, context)
                .flatMapMany(enrichedMessage ->
                        Flux.from(delegate.subscriptionQuery(enrichedMessage, responseType, context))
                );
    }

    @SuppressWarnings("unchecked")
    private Mono<QueryMessage> dispatchThroughChain(Object query, ProcessingContext context) {
        QueryMessage queryMessage;
        if (query instanceof QueryMessage qm) {
            queryMessage = qm;
        } else if (query instanceof Message m) {
            queryMessage = new GenericQueryMessage(m);
        } else {
            queryMessage = new GenericQueryMessage(messageTypeResolver.resolveOrThrow(query), query);
        }
        return (Mono<QueryMessage>) interceptorChain.proceed(queryMessage, context);
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("messageTypeResolver", messageTypeResolver);
    }

    private static ReactorMessageDispatchInterceptorChain<QueryMessage> buildChain(
            List<ReactorMessageDispatchInterceptor<? super QueryMessage>> interceptors
    ) {
        ReactorMessageDispatchInterceptorChain<QueryMessage> chain = (message, ctx) -> Mono.just(message);
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            // Safe cast: each interceptor in the list can handle QueryMessage,
            // because they accept "QueryMessage or a supertype of QueryMessage".
            //noinspection rawtypes,unchecked
            var interceptor = (ReactorMessageDispatchInterceptor) interceptors.get(i);
            var next = chain;
            //noinspection unchecked
            chain = (message, ctx) -> interceptor.interceptOnDispatch(message, ctx, next);
        }
        return chain;
    }
}
