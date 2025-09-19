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

package org.axonframework.queryhandling.interceptors;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * @author Steven van Beelen
 * @since 5.0.0
 */
// TODO 3595 - Introduce monitoring logic here.
public class InterceptingQueryUpdateEmitter implements QueryUpdateEmitter {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final QueryUpdateEmitter delegate;
    private final List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> dispatchInterceptors;

    /**
     *
     * @param delegate
     * @param dispatchInterceptors
     */
    public InterceptingQueryUpdateEmitter(
            @Nonnull QueryUpdateEmitter delegate,
            @Nonnull List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> dispatchInterceptors
    ) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate QueryUpdateEmitter must not be null.");
        this.dispatchInterceptors =
                Objects.requireNonNull(dispatchInterceptors, "The dispatch interceptors must not be null.");
    }

    @Override
    public void emit(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                     @Nonnull SubscriptionQueryUpdateMessage update) {
        /*
        // TODO #3488 - Reintegrate, and construct chain only once!
        return new DefaultMessageDispatchInterceptorChain<>(dispatchInterceptors)
                .proceed(message, null)
                .first()
                .<SubscriptionQueryUpdateMessage<U>>cast()
                .asMono()
                .map(MessageStream.Entry::message)
                .block();
         */
        delegate.emit(filter, update);
    }

    @Override
    public void complete(@Nonnull Predicate<SubscriptionQueryMessage> filter) {

    }

    @Override
    public void completeExceptionally(@Nonnull Predicate<SubscriptionQueryMessage> filter, @Nonnull Throwable cause) {

    }

    @Nonnull
    @Override
    public UpdateHandler subscribe(@Nonnull SubscriptionQueryMessage query,
                                   int updateBufferSize) {
        return null;
    }
}
