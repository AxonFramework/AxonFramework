/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.queryhandling;

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

    private final Mono<I> initialResult;
    private final Flux<U> updates;

    /**
     * Initializes the result with mono and flux used for result retrieval.
     *
     * @param initialResult mono representing initial result
     * @param updates       flux representing incremental updates
     */
    public DefaultSubscriptionQueryResult(Mono<I> initialResult, Flux<U> updates) {
        this.initialResult = initialResult;
        this.updates = updates;
    }

    @Override
    public Mono<I> initialResult() {
        return initialResult;
    }

    @Override
    public Flux<U> updates() {
        return updates;
    }
}
