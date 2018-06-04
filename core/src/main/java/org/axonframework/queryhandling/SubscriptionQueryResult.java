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
 * Result of the subscription query. It contains publishers for initial result and incremental updates. Until there is a
 * subscription to the publisher, nothing happens.
 *
 * @author Milan Savic
 * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, SubscriptionQueryBackpressure)
 * @since 3.3
 */
public interface SubscriptionQueryResult<I, U> {

    /**
     * Subscribing to this mono will trigger invocation of query handler. The return value of that query handler will be
     * passed to the mono, once it's ready.
     *
     * @return the mono representing the initial result
     */
    Mono<I> initialResult();

    /**
     * When there is an update to the subscription query, it will be emitted to this flux.
     *
     * @return the flux representing the incremental updates
     */
    Flux<U> updates();
}
