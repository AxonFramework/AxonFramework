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
import org.axonframework.messaging.eventhandling.EventMessage;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Reactive event gateway that runs dispatch interceptors inside the Reactor subscription.
 * <p>
 * Use this instead of {@link org.axonframework.messaging.eventhandling.gateway.EventGateway} when interceptors need
 * access to Reactor context (e.g., {@code ReactiveSecurityContextHolder}).
 * <p>
 * The interceptor chain is built and executed within the Reactor subscription, ensuring that the Reactor
 * {@link reactor.util.context.Context} is available throughout the entire dispatch pipeline.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 * @see org.axonframework.messaging.eventhandling.gateway.EventGateway
 * @see ReactiveMessageDispatchInterceptor
 */
public interface ReactiveEventGateway {

    /**
     * Publishes the given {@code events} and returns a {@link Mono} that completes when publishing is done.
     *
     * @param events The events to publish.
     * @return A {@link Mono} completing when the events have been published.
     */
    @Nonnull
    Mono<Void> publish(@Nonnull Object... events);

    /**
     * Publishes the given list of {@code events} and returns a {@link Mono} that completes when publishing is done.
     *
     * @param events The events to publish.
     * @return A {@link Mono} completing when the events have been published.
     */
    @Nonnull
    Mono<Void> publish(@Nonnull List<?> events);

    /**
     * Registers a {@link ReactiveMessageDispatchInterceptor} that will be applied to all events published through this
     * gateway.
     *
     * @param interceptor The interceptor to register.
     */
    void registerDispatchInterceptor(
            @Nonnull ReactiveMessageDispatchInterceptor<EventMessage> interceptor
    );
}
