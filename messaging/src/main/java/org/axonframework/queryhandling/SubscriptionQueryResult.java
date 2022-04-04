/*
 * Copyright (c) 2010-2022. Axon Framework
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
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

/**
 * Result of the subscription query. It contains publishers for initial result and incremental updates. Until there is a
 * subscription to the publisher, nothing happens.
 *
 * @author Milan Savic
 * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, int)
 * @since 3.3
 */
public interface SubscriptionQueryResult<I, U> extends Registration {

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

    /**
     * Delegates handling of initial result and incremental updates to the provided consumers. Subscription to the
     * incremental updates is done after the initial result is retrieved and its consumer is invoked. If anything goes
     * wrong during invoking or consuming initial result or incremental updates, subscription is cancelled.
     *
     * @param initialResultConsumer The consumer to be invoked when the initial result is retrieved
     * @param updateConsumer        The consumer to be invoked when incremental updates are emitted
     */
    default void handle(Consumer<? super I> initialResultConsumer, Consumer<? super U> updateConsumer) {
        initialResult().subscribe(initialResult -> {
            try {
                initialResultConsumer.accept(initialResult);
                updates().subscribe(i -> {
                    try {
                        updateConsumer.accept(i);
                    } catch (Exception e) {
                        cancel();
                    }
                });
            } catch (Exception e) {
                cancel();
            }
        }, t -> cancel());
    }
}
