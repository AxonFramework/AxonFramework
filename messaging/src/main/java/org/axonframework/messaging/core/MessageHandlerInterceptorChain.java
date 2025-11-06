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
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

/**
 * An interceptor chain that manages the flow of a {@link Message} of type {@code M} through a chain of
 * {@link MessageHandlerInterceptor interceptors}, and ultimately to a message handler.
 * <p>
 * Interceptors may continue processing via this chain by calling the {@link #proceed(Message, ProcessingContext)}
 * method. Alternatively, they can block processing by returning without calling either of these methods.
 *
 * @param <M> The type of {@link Message} intercepted by this chain.
 * @author Allard Buijze
 * @author Simon Zambrovski
 * @since 0.5.0
 */
@FunctionalInterface
public interface MessageHandlerInterceptorChain<M extends Message> {

    /**
     * Signals this interceptor chain to continue processing the {@code message}.
     *
     * @param message The message to pass down the chain.
     * @param context The active processing context the given {@code message} is being processed in.
     * @return A {@link MessageStream} containing the result of processing the given {@code message}.
     */
    @Nonnull
    MessageStream<?> proceed(@Nonnull M message, @Nonnull ProcessingContext context);
}
