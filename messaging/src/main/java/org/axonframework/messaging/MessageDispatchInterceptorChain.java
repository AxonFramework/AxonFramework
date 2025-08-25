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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.unitofwork.ProcessingContext;

/**
 * The interceptor chain manages the flow of a message through a chain of interceptors. Interceptors may continue
 * processing via this chain by calling the {@link #proceed(Message, ProcessingContext)} method. Alternatively, they can
 * block processing by returning without calling this method.
 *
 * @param <M> Type of message.
 *
 * @since 5.0.0
 * @author Allard Buijze
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @author Simon Zambrovski
 */
@FunctionalInterface
public interface MessageDispatchInterceptorChain<M extends Message<?>> {

    /**
     * Signals the Interceptor Chain to continue processing the message.
     *
     * @param message message to pass down the chain.
     * @param context The {@link ProcessingContext} in which the reset is being prepared.
     * @return A {@link MessageStream} containing the result of the message processing.
     */
    @Nonnull
    MessageStream<?> proceed(@Nonnull M message, @Nullable ProcessingContext context);
}
