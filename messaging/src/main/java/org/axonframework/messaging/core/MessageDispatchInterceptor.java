/*
 * Copyright (c) 2010-2025. Axon Framework
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
import jakarta.annotation.Nullable;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.function.Function;

/**
 * Interceptor that allows {@link Message messages} to be intercepted and modified before they are dispatched.
 * <p>
 * This interceptor provides a very early means to alter or reject message, even before any {@link ProcessingContext} is
 * created.
 *
 * @param <M> The message type this interceptor can process.
 * @author Allard Buijze
 * @author Simon Zambrovski
 * @since 2.0.0
 */
public interface MessageDispatchInterceptor<M extends Message> {

    /**
     * Intercepts a given {@code message} on dispatching.
     * <p/>
     * The implementer of this method might want to intercept the message before passing it to the chain (effectively
     * before calling {@link MessageDispatchInterceptorChain#proceed(Message, ProcessingContext)}) or after the chain
     * (by mapping the resulting message by calling {@link MessageStream#mapMessage(Function)}).
     *
     * @param message          The message to intercept on dispatching.
     * @param context          The active processing context, if any. Can be used to (e.g.) validate correlation data.
     * @param interceptorChain The interceptor chain to signal that processing is finished and further interceptors
     *                         should be called.
     * @return The resulting message stream from
     * {@link MessageDispatchInterceptorChain#proceed(Message, ProcessingContext)}.
     */
    @Nonnull
    MessageStream<?> interceptOnDispatch(@Nonnull M message,
                                         @Nullable ProcessingContext context,
                                         @Nonnull MessageDispatchInterceptorChain<M> interceptorChain);
}
