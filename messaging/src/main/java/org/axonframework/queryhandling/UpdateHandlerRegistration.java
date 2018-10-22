/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.common.Registration;
import reactor.core.publisher.Flux;

/**
 * Result of registering an Update Handler. Contains a {@link Flux} with updates and {@link Registration} to be
 * cancelled when we're no longer interested in updates.
 *
 * @author Milan Savic
 * @since 4.0
 */
public class UpdateHandlerRegistration<U> {

    private final Registration registration;
    private final Flux<SubscriptionQueryUpdateMessage<U>> updates;

    /**
     * Constructs a {@link UpdateHandlerRegistration} with given {@code registration} and {@code updates}.
     *
     * @param registration used to cancel the registration
     * @param updates      used to subscribe to updates stream
     */
    public UpdateHandlerRegistration(Registration registration, Flux<SubscriptionQueryUpdateMessage<U>> updates) {
        this.registration = registration;
        this.updates = updates;
    }

    /**
     * Gets the registration.
     *
     * @return the registration used for cancellation of updates
     */
    public Registration getRegistration() {
        return registration;
    }

    /**
     * Gets the updates.
     *
     * @return a {@link Flux} for subscribing to the update stream
     */
    public Flux<SubscriptionQueryUpdateMessage<U>> getUpdates() {
        return updates;
    }
}
