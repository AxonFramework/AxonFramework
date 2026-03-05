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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import reactor.core.publisher.Mono;

/**
 * Reactor equivalent of {@link org.axonframework.messaging.core.MessageDispatchInterceptorChain}.
 * <p>
 * Each link in the chain processes or enriches the message before passing it to the next link. The terminal link
 * returns the message as-is.
 *
 * @param <M> the type of {@link Message} this chain handles
 * @author Milan Savic
 * @author Theo Emanuelsson
 * @since 4.4.2
 */
@FunctionalInterface
public interface ReactorMessageDispatchInterceptorChain<M extends Message> {

    /**
     * Proceeds with the next interceptor in the chain, or returns the message as-is if this is the terminal link.
     *
     * @param message the message to process
     * @param context the active processing context, if any
     * @return a {@link Mono} that completes with the (possibly enriched) message
     */
    @NonNull
    Mono<?> proceed(@NonNull M message, @Nullable ProcessingContext context);
}
