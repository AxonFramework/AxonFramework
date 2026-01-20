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

package org.axonframework.messaging.queryhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

/**
 * Exception thrown whenever {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int)} is
 * invoked multiple times for the same {@link SubscriptionQueryMessage}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class SubscriptionQueryAlreadyRegisteredException extends RuntimeException {

    /**
     * Constructs a {@code SubscriptionQueryAlreadyRegisteredException} for the given {@code queryId}.
     *
     * @param queryId The {@link SubscriptionQueryMessage#identifier()} of the subscription query accidentally being
     *                registered multiple times.
     */
    public SubscriptionQueryAlreadyRegisteredException(@Nonnull String queryId) {
        super("There is already a subscription query with query identifier [" + queryId + "] present.");
    }
}
