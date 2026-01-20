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

package org.axonframework.messaging.core;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

/**
 * Workflow interface that allows for customized message handler invocation chains. A MessageHandlerInterceptor can add
 * customized behavior to message handler invocations, both before and after the invocation.
 *
 * @param <M> The message type this interceptor can process.
 * @author Allard Buijze
 * @author Simon Zambrovski
 * @since 0.5.0
 */
public interface MessageHandlerInterceptor<M extends Message> {

    /**
     * Intercepts a given {@code message} on handling before reaching the designated
     * {@link MessageHandler}.
     * <p/>
     * The interceptor is responsible for the continuation of the handling process by invoking the
     * {@link MessageHandlerInterceptorChain#proceed(Message, ProcessingContext)} method on the given
     * {@code interceptorChain}.
     * <p/>
     * The given {@code context} contains contextual information. Any information gathered by interceptors may be
     * attached to the context.
     * <p/>
     * Interceptors are not allowed to change the type of the message handling result, as the dispatching component
     * expects a result of a specific type.
     *
     * @param message          The message to intercept on handling.
     * @param context          The active processing context of the {@code message} being processed.
     * @param interceptorChain The interceptor chain that allows this interceptor to proceed the dispatch process.
     * @return The resulting message stream from
     * {@link MessageHandlerInterceptorChain#proceed(Message, ProcessingContext)}.
     */
    @Nonnull
    MessageStream<?> interceptOnHandle(@Nonnull M message,
                                       @Nonnull ProcessingContext context,
                                       @Nonnull MessageHandlerInterceptorChain<M> interceptorChain);
}
