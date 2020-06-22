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

package org.axonframework.messaging.reactive;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.ResultMessage;
import reactor.core.publisher.Flux;

/**
 * Interceptor that allows results to be intercepted and modified before they are handled. Implementations are required
 * to operate on a {@link Flux} of results or return a new {@link Flux} which will be passed down the interceptor chain.
 * Also, implementations may make decisions based on the message that was dispatched.
 *
 * @param <M> The type of the message for which the result is going to be intercepted
 * @param <R> The type of the result to be intercepted
 * @author Sara Pellegrini
 * @since 4.4
 */
@FunctionalInterface
public interface ReactorResultHandlerInterceptor<M extends Message<?>, R extends ResultMessage<?>> {

    /**
     * Intercepts result messages. It's possible to break the interceptor chain by returning {@link Flux#empty()} or
     * {@link Flux#error(Throwable)} variations.
     *
     * @param message a message that was dispatched (and caused these {@code results})
     * @param results results of a dispatched message
     * @return intercepted results
     */
    Flux<R> intercept(M message, Flux<R> results);
}
