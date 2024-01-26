/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interceptor that allows messages to be intercepted and modified before they are dispatched. This interceptor provides
 * a very early means to alter or reject Messages, even before any Unit of Work is created.
 *
 * @param <T> The message type this interceptor can process
 * @author Allard Buijze
 * @since 2.0
 */
public interface MessageDispatchInterceptor<T extends Message<?>> {

    /**
     * Invoked each time a message is about to be dispatched. The given {@code message} represents the message being
     * dispatched.
     *
     * @param message The message intended to be dispatched
     * @return the message to dispatch
     */
    @Deprecated
    @Nonnull
    default T handle(@Nonnull T message) {
        return handle(Collections.singletonList(message)).apply(0, message);
    }

    /**
     * Apply this interceptor to the given list of {@code messages}. This method returns a function that can be invoked
     * to obtain a modified version of messages at each position in the list.
     *
     * @param messages The Messages to pre-process
     * @return a function that processes messages based on their position in the list
     */
    @Deprecated
    @Nonnull
    BiFunction<Integer, T, T> handle(@Nonnull List<? extends T> messages);


    default <M extends T, R> MessageStream<? extends R> interceptOnDispatch(@Nonnull M message,
                                                                            @Nullable ProcessingContext context,
                                                                            @Nonnull InterceptorChain<M, R> interceptorChain) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
