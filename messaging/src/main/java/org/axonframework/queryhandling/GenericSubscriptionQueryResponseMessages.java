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

package org.axonframework.queryhandling;

import jakarta.annotation.Nonnull;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * Generic implementation of the {@link SubscriptionQueryResponseMessages} interface.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class GenericSubscriptionQueryResponseMessages implements SubscriptionQueryResponseMessages {

    private final Flux<QueryResponseMessage> initialResult;
    private final Flux<SubscriptionQueryUpdateMessage> updates;
    private final Runnable closeCallback;

    /**
     * Constructs a {@code GenericSubscriptionQueryResponseMessages} with the given {@code initialResult},
     * {@code updates}, and {@code closeCallback}.
     *
     * @param initialResult The {@code Flux} of {@link QueryResponseMessage QueryResponseMessages} returned on
     *                      {@link #initialResult()}.
     * @param updates       The {@code Flux} of {@link SubscriptionQueryUpdateMessage SubscriptionQueryUpdateMessages}
     *                      returned on {@link #updates()}.
     * @param closeCallback A {@code Runnable} to {@link #close()} this {@code SubscriptionQueryResponseMessages}.
     */
    public GenericSubscriptionQueryResponseMessages(@Nonnull Flux<QueryResponseMessage> initialResult,
                                                    @Nonnull Flux<SubscriptionQueryUpdateMessage> updates,
                                                    @Nonnull Runnable closeCallback) {
        this.initialResult = Objects.requireNonNull(initialResult, "The initial result must not be null.");
        this.updates = Objects.requireNonNull(updates, "The updates must not be null.");
        this.closeCallback = Objects.requireNonNull(closeCallback, "The close callback must not be null.");
    }

    @Nonnull
    @Override
    public Flux<QueryResponseMessage> initialResult() {
        return initialResult;
    }

    @Nonnull
    @Override
    public Flux<SubscriptionQueryUpdateMessage> updates() {
        return updates;
    }

    @Override
    public void close() {
        closeCallback.run();
    }
}
