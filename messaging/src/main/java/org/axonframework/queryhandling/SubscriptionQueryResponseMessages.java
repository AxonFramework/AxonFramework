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
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.function.Predicate;

/**
 * Container of the {@link #initialResult() initial result} and subsequent {@link #updates() updates} from a
 * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int)}.
 * <p>
 * Until there is a subscription on either of the publishers of this response, nothing happens.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int)
 * @since 5.0.0
 */
public interface SubscriptionQueryResponseMessages {

    /**
     * Returns the {@code Flux} of initial {@link QueryResponseMessage QueryResponseMessages} from hitting a
     * {@link QueryHandler} through {@link QueryBus#query(QueryMessage, ProcessingContext)}.
     * <p>
     * The {@code QueryHandler} will not be invoked until the resulting {@code Flux} is subscribed to. The return
     * value(s) of the {@code QueryHandler} will be passed to the {@code Flux}, once it's ready.
     *
     * @return The {@code Flux} of initial {@link QueryResponseMessage QueryResponseMessages} from hitting a
     * {@link QueryHandler} through {@link QueryBus#query(QueryMessage, ProcessingContext)}.
     */
    @Nonnull
    Flux<QueryResponseMessage> initialResult();

    /**
     * Returns the {@code Flux} of {@link SubscriptionQueryUpdateMessage SubscriptionQueryUpdateMessages} sent to the
     * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int) subscription query}.
     * <p>
     * Updates are typically emitted through
     * {@link QueryBus#emitUpdate(Predicate, SubscriptionQueryUpdateMessage, ProcessingContext)}.
     * <p>
     * When there is an update to the subscription query that resulted in {@code this} response, it will be emitted to
     * the returned {@code Flux}.
     *
     * @return The {@code Flux} of {@link SubscriptionQueryUpdateMessage SubscriptionQueryUpdateMessages} sent to the
     * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int) subscription query}.
     */
    @Nonnull
    Flux<SubscriptionQueryUpdateMessage> updates();

    /**
     * Returns the {@link #initialResult()} and {@link #updates()}, {@link Flux#concatWith(Publisher) concatted} with
     * one another, both as {@link QueryResponseMessage QueryResponseMessages}.
     * <p>
     * Will map all {@link SubscriptionQueryUpdateMessage SubscriptionQueryUpdateMessages} from {@code updated()} to
     * {@link QueryResponseMessage QueryResponseMessages}.
     *
     * @return The {@link #initialResult()} and {@link #updates()}, {@link Flux#concatWith(Publisher) concatted} with
     * one another, both as {@link QueryResponseMessage QueryResponseMessages}.
     */
    @Nonnull
    default Flux<QueryResponseMessage> asFlux() {
        return initialResult().concatWith(updates().map(GenericQueryResponseMessage::new));
    }

    /**
     * Closes this {@code SubscriptionQueryResponseMessages} canceling and/or closing any subscriptions backing the
     * response messages returned by {@link #initialResult()} and {@link #updates()}.
     */
    void close();
}
