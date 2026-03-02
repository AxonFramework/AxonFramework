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

import jakarta.annotation.Nonnull;
import org.axonframework.extension.reactor.messaging.core.ReactiveMessageDispatchInterceptor;
import org.axonframework.extension.reactor.messaging.core.ReactiveMessageDispatchInterceptorChain;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.gateway.EventGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of {@link ReactiveEventGateway}.
 * <p>
 * Builds a recursive interceptor chain that runs inside the Reactor subscription, then delegates to the Axon Framework
 * {@link EventGateway}.
 * <p>
 * Each event payload is converted to an {@link EventMessage}, run through the interceptor chain (which may enrich
 * metadata), and then passed to the delegate gateway. Since {@link EventGateway#publish(List)} preserves objects that
 * are already {@link EventMessage} instances, the enriched metadata is retained.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 * @see ReactiveEventGateway
 * @see ReactiveMessageDispatchInterceptor
 */
public class DefaultReactiveEventGateway implements ReactiveEventGateway {

    private final EventGateway delegate;
    private final MessageTypeResolver messageTypeResolver;
    private final List<ReactiveMessageDispatchInterceptor<EventMessage>> interceptors;

    /**
     * Instantiate a {@link DefaultReactiveEventGateway} based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link DefaultReactiveEventGateway} instance.
     */
    protected DefaultReactiveEventGateway(Builder builder) {
        this.delegate = builder.eventGateway;
        this.messageTypeResolver = builder.messageTypeResolver;
        this.interceptors = new CopyOnWriteArrayList<>(builder.dispatchInterceptors);
    }

    /**
     * Instantiate a {@link Builder} to construct a {@link DefaultReactiveEventGateway}.
     *
     * @return A {@link Builder} to construct a {@link DefaultReactiveEventGateway}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Nonnull
    @Override
    public Mono<Void> publish(@Nonnull Object... events) {
        return publish(Arrays.asList(events));
    }

    @Nonnull
    @Override
    public Mono<Void> publish(@Nonnull List<?> events) {
        return Flux.fromIterable(events)
                   .map(this::asEventMessage)
                   .flatMap(this::dispatchThroughChain)
                   .collectList()
                   .flatMap(enrichedMessages ->
                           Mono.fromFuture(delegate.publish(enrichedMessages))
                   );
    }

    @Override
    public void registerDispatchInterceptor(
            @Nonnull ReactiveMessageDispatchInterceptor<EventMessage> interceptor
    ) {
        interceptors.add(interceptor);
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

    private Mono<EventMessage> dispatchThroughChain(EventMessage eventMessage) {
        return buildChain().proceed(eventMessage);
    }

    private ReactiveMessageDispatchInterceptorChain<EventMessage> buildChain() {
        ReactiveMessageDispatchInterceptorChain<EventMessage> chain = Mono::just;
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            var interceptor = interceptors.get(i);
            var next = chain;
            chain = message -> interceptor.interceptOnDispatch(message, next);
        }
        return chain;
    }

    /**
     * Builder class to instantiate a {@link DefaultReactiveEventGateway}.
     * <p>
     * The {@link EventGateway} and {@link MessageTypeResolver} are <b>hard requirements</b> and as such should be
     * provided.
     */
    public static class Builder {

        private EventGateway eventGateway;
        private MessageTypeResolver messageTypeResolver;
        private List<ReactiveMessageDispatchInterceptor<EventMessage>> dispatchInterceptors = new ArrayList<>();

        /**
         * Sets the {@link EventGateway} to delegate event publishing to.
         *
         * @param eventGateway The {@link EventGateway} to delegate to.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder eventGateway(@Nonnull EventGateway eventGateway) {
            this.eventGateway = Objects.requireNonNull(eventGateway, "EventGateway may not be null");
            return this;
        }

        /**
         * Sets the {@link MessageTypeResolver} used to resolve the message type from event payloads.
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
         * Sets the list of {@link ReactiveMessageDispatchInterceptor}s to be applied to events.
         *
         * @param dispatchInterceptors The interceptors to register.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder dispatchInterceptors(
                @Nonnull List<ReactiveMessageDispatchInterceptor<EventMessage>> dispatchInterceptors
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
            Objects.requireNonNull(eventGateway, "The EventGateway is a hard requirement and should be provided");
            Objects.requireNonNull(
                    messageTypeResolver, "The MessageTypeResolver is a hard requirement and should be provided"
            );
        }

        /**
         * Initializes a {@link DefaultReactiveEventGateway} as specified through this Builder.
         *
         * @return A {@link DefaultReactiveEventGateway} as specified through this Builder.
         */
        public DefaultReactiveEventGateway build() {
            validate();
            return new DefaultReactiveEventGateway(this);
        }
    }
}
