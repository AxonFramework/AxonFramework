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

package org.axonframework.extension.reactor.messaging.core;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Message;
import reactor.core.publisher.Mono;

/**
 * Reactive equivalent of {@link org.axonframework.messaging.core.MessageDispatchInterceptor}.
 * <p>
 * Runs inside the Reactor subscription, giving access to Reactor's context (e.g.,
 * {@code ReactiveSecurityContextHolder}) which is not available in Axon Framework's native
 * {@link org.axonframework.messaging.core.MessageDispatchInterceptor}.
 * <p>
 * Implementations intercept a message before dispatch, potentially enriching it (e.g., adding metadata), and must call
 * {@link ReactiveMessageDispatchInterceptorChain#proceed(Message) chain.proceed(message)} to continue the chain.
 *
 * @param <M> The type of {@link Message} this interceptor handles.
 * @author Theo Emanuelsson
 * @since 5.1.0
 */
@FunctionalInterface
public interface ReactiveMessageDispatchInterceptor<M extends Message> {

    /**
     * Intercepts a message before dispatch. Implementations may enrich the message (e.g., add metadata) and must call
     * {@link ReactiveMessageDispatchInterceptorChain#proceed(Message) chain.proceed(message)} to continue the chain.
     *
     * @param message The message being dispatched.
     * @param chain   The remaining interceptor chain.
     * @return A {@link Mono} that completes with the (possibly enriched) message.
     */
    @Nonnull
    Mono<M> interceptOnDispatch(@Nonnull M message, @Nonnull ReactiveMessageDispatchInterceptorChain<M> chain);
}
