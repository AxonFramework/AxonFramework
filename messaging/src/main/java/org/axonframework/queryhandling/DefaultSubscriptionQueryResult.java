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
import org.axonframework.messaging.MessageStream;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link SubscriptionQueryResult}.
 *
 * @param <I> The type of initial result
 * @param <U> The type of incremental updates
 * @author Milan Savic
 * @since 3.3
 */
public class DefaultSubscriptionQueryResult<I, U> implements SubscriptionQueryResult<I, U> {

    private final MessageStream<QueryResponseMessage> initialResponses;
    private final Flux<U> updates;
    private final Registration registrationDelegate;

    /**
     * Initializes the result with mono and flux used for result retrieval.
     *
     * @param initialResult        mono representing initial result
     * @param updates              flux representing incremental updates
     * @param registrationDelegate delegate which cancels the registration of this result
     */
    public DefaultSubscriptionQueryResult(@Nonnull MessageStream<QueryResponseMessage> initialResponses,
                                          @Nonnull Flux<U> updates,
                                          @Nonnull Registration registrationDelegate) {
        this.initialResponses = initialResponses;
        this.updates = updates;
        this.registrationDelegate = registrationDelegate;
    }

    @Override
    public Mono<I> initialResult() {
        return initialResponses.asFlux().then(Mono.empty());
    }

    @Override
    public Flux<U> updates() {
        return updates;
    }

    @Override
    public boolean cancel() {
        return registrationDelegate.cancel();
    }
}
