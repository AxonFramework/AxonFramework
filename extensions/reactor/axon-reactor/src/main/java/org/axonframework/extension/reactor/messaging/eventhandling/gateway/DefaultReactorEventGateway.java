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

package org.axonframework.extension.reactor.messaging.eventhandling.gateway;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptor;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptorChain;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.gateway.EventGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Default implementation of {@link ReactorEventGateway}.
 * <p>
 * Builds a recursive interceptor chain that runs inside the Reactor subscription, then delegates to the Axon Framework
 * {@link EventGateway}.
 * <p>
 * Each event payload is converted to an {@link EventMessage}, run through the interceptor chain (which may enrich
 * metadata), and then passed to the delegate gateway. Since {@link EventGateway#publish(List)} preserves objects that
 * are already {@link EventMessage} instances, the enriched metadata is retained.
 *
 * @author Milan Savic
 * @author Theo Emanuelsson
 * @since 4.4.2
 * @see ReactorEventGateway
 * @see ReactorMessageDispatchInterceptor
 */
public class DefaultReactorEventGateway implements ReactorEventGateway {

    private final EventGateway delegate;
    private final MessageTypeResolver messageTypeResolver;
    private final List<ReactorMessageDispatchInterceptor<? super EventMessage>> interceptors;

    /**
     * Instantiate a {@link DefaultReactorEventGateway}.
     *
     * @param eventGateway         the {@link EventGateway} to delegate event publishing to
     * @param messageTypeResolver  the {@link MessageTypeResolver} for resolving message types
     * @param dispatchInterceptors the list of {@link ReactorMessageDispatchInterceptor}s to apply to events
     */
    public DefaultReactorEventGateway(
            @NonNull EventGateway eventGateway,
            @NonNull MessageTypeResolver messageTypeResolver,
            @NonNull List<ReactorMessageDispatchInterceptor<? super EventMessage>> dispatchInterceptors
    ) {
        this.delegate = Objects.requireNonNull(eventGateway, "EventGateway may not be null");
        this.messageTypeResolver = Objects.requireNonNull(messageTypeResolver, "MessageTypeResolver may not be null");
        this.interceptors = List.copyOf(
                Objects.requireNonNull(dispatchInterceptors, "Dispatch interceptors may not be null")
        );
    }

    /**
     * Instantiate a {@link DefaultReactorEventGateway} without dispatch interceptors.
     *
     * @param eventGateway        the {@link EventGateway} to delegate event publishing to
     * @param messageTypeResolver the {@link MessageTypeResolver} for resolving message types
     */
    public DefaultReactorEventGateway(
            @NonNull EventGateway eventGateway,
            @NonNull MessageTypeResolver messageTypeResolver
    ) {
        this(eventGateway, messageTypeResolver, List.of());
    }

    @NonNull
    @Override
    public Mono<Void> publish(@Nullable ProcessingContext context, @NonNull List<?> events) {
        return Flux.fromIterable(events)
                   .map(this::asEventMessage)
                   .flatMap(eventMessage -> dispatchThroughChain(eventMessage, context))
                   .collectList()
                   .flatMap(enrichedMessages ->
                           Mono.fromFuture(delegate.publish(context, enrichedMessages))
                   );
    }

    private EventMessage asEventMessage(Object event) {
        if (event instanceof EventMessage e) {
            return e;
        }
        if (event instanceof Message m) {
            return new GenericEventMessage(m, Instant::now);
        }
        return new GenericEventMessage(
                messageTypeResolver.resolveOrThrow(event),
                event,
                Metadata.emptyInstance()
        );
    }

    @SuppressWarnings("unchecked")
    private Mono<EventMessage> dispatchThroughChain(EventMessage eventMessage, ProcessingContext context) {
        return (Mono<EventMessage>) buildChain().proceed(eventMessage, context);
    }

    private ReactorMessageDispatchInterceptorChain<EventMessage> buildChain() {
        ReactorMessageDispatchInterceptorChain<EventMessage> chain = (message, ctx) -> Mono.just(message);
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            // Safe cast: each interceptor in the list can handle EventMessage,
            // because they accept "EventMessage or a supertype of EventMessage".
            //noinspection rawtypes,unchecked
            var interceptor = (ReactorMessageDispatchInterceptor) interceptors.get(i);
            var next = chain;
            //noinspection unchecked
            chain = (message, ctx) -> interceptor.interceptOnDispatch(message, ctx, next);
        }
        return chain;
    }
}
