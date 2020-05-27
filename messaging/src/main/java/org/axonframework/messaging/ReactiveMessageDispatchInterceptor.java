/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Interceptor that allows messages to be intercepted and modified before they are dispatched. Implementations are
 * required to provide a function that modifies a {@link Mono} of a message and returns a modified/new {@code Mono} to
 * be passed down the interceptor chain or to be dispatched. This way we provide a reactive approach to intercepting
 * messages.
 *
 * @param <M> the message type this interceptor can process
 * @author Milan Savic
 * @since 4.4
 */
@FunctionalInterface
public interface ReactiveMessageDispatchInterceptor<M> extends
        MessageDispatchInterceptor {

    /**
     * Intercepts a message.
     *
     * @param message a {@link Mono} of a message to be intercepted
     * @return the message {@link Mono} to dispatch
     */
    Mono<M> intercept(Mono<M> message);

    @Override
    default BiFunction handle(List messages) {
        return null; //TODO
    }
}
