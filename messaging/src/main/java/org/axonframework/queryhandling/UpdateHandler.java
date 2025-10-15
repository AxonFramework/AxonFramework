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
import org.axonframework.common.Registration;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * The update handler resulting from {@link QueryBus#subscribeToUpdates(SubscriptionQueryMessage, int)}.
 * <p>
 * Contains a {@link Flux} with {@link #updates()} and a {@link Registration} to be cancelled when we're no longer
 * interested in updates.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 4.0.0
 */
public final class UpdateHandler {

    private final Flux<SubscriptionQueryUpdateMessage> updates;
    private final Runnable cancelRegistration;
    private final Runnable completeHandler;

    /**
     * Constructs an {@code UpdateHandler} for the given {@code updates}, {@code cancelRegistration}, and
     * {@code completeHandler}.
     *
     * @param updates            The {@code Flux} of updates from this update handler.
     * @param cancelRegistration Used to cancel the registration of this {@code UpdateHandler} on {@link #cancel()}.
     * @param completeHandler    Used to complete this {@code UpdateHandler} on {@link #complete()}.
     *
     */
    public UpdateHandler(@Nonnull Flux<SubscriptionQueryUpdateMessage> updates,
                         @Nonnull Runnable cancelRegistration,
                         @Nonnull Runnable completeHandler) {
        this.updates = Objects.requireNonNull(updates, "The updates must not be null.");
        this.cancelRegistration =
                Objects.requireNonNull(cancelRegistration, "The cancel registration runnable must not be null.");
        this.completeHandler = Objects.requireNonNull(completeHandler,
                                                      "The complete handler runnable must not be null.");
    }

    /**
     * Returns a {@link Flux} of {@link SubscriptionQueryUpdateMessage} emitted on this {@code UpdateHandler}.
     *
     * @return A {@link Flux} of {@link SubscriptionQueryUpdateMessage} emitted on this {@code UpdateHandler}.
     */
    @Nonnull
    public Flux<SubscriptionQueryUpdateMessage> updates() {
        return updates;
    }

    /**
     * Cancel the registration of this {@code UpdateHandler} with the {@link QueryUpdateEmitter} it originated from.
     */
    public void cancel() {
        cancelRegistration.run();
    }

    /**
     * Completes the {@link #updates()}.
     * <p>
     * The consumer can use this method to indicate it is no longer interested in updates. This operation automatically
     * {@link #cancel() cancels the registration} to ensure updates that have not been subscribed to do not linger in
     * the {@link QueryUpdateEmitter}.
     */
    public void complete() {
        completeHandler.run();
        cancelRegistration.run();
    }
}
