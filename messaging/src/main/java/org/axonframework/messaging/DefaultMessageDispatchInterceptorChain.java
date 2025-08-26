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
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.BiFunction;

/**
 * Default implementation for a dispatch handler interceptor chain.
 *
 * @param <M> type of message to intercept.
 * @author Simon Zambrovski
 * @since 5.0.0
 */
@Internal
public class DefaultMessageDispatchInterceptorChain<M extends Message>
        implements MessageDispatchInterceptorChain<M> {

    private final Iterator<MessageDispatchInterceptor<M>> chain;
    private final BiFunction<? super M, ProcessingContext, MessageStream<?>> terminal;

    /**
     * Constructs a message dispatch interceptor chain without a target handler.
     * <p/>
     *
     * @param dispatchInterceptors list of interceptors.
     */
    public DefaultMessageDispatchInterceptorChain(
            @Nonnull Collection<MessageDispatchInterceptor<? super M>> dispatchInterceptors
    ) {
        this(dispatchInterceptors, (message, processingContext) -> MessageStream.just(message).cast());
    }

    /**
     * Constructs a message dispatch interceptor chain with a list of interceptors and a target handler.
     *
     * @param dispatchInterceptors list of dispatch interceptors.
     * @param terminal             function to be invoked after the chain processing.
     */
    public DefaultMessageDispatchInterceptorChain(
            @Nonnull Collection<MessageDispatchInterceptor<? super M>> dispatchInterceptors,
            BiFunction<? super M, ProcessingContext, MessageStream<?>> terminal
    ) {
        // Safe cast: each interceptor in the list can handle M,
        // because they accept "M or a supertype of M".
        //noinspection unchecked
        this.chain = (Iterator<MessageDispatchInterceptor<M>>) (Iterator<?>) dispatchInterceptors.iterator();
        this.terminal = terminal;
    }

    @Override
    public @Nonnull MessageStream<?> proceed(
            @Nonnull M message,
            @Nullable ProcessingContext context
    ) {
        try {
            if (chain.hasNext()) {
                return chain.next().interceptOnDispatch(message,
                                                        context, this);
            } else {
                return terminal.apply(message, context);
            }
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }
}
