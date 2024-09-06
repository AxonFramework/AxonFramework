/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.invoke.MethodHandles;
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

    Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
     * Delegates handling of initial result and incremental updates to the provided consumers.
     * <p>
     * Subscription to the incremental updates is done after the initial result is retrieved and its consumer is
     * invoked. If anything goes wrong during invoking or consuming initial result or incremental updates, subscription
     * is cancelled.
     *
     * @param initialResultConsumer The {@link Consumer} to be invoked when the initial result is retrieved.
     * @param updateConsumer        The {@link Consumer} to be invoked when incremental updates are emitted.
     * @deprecated Please use {@link #handle(Consumer, Consumer, Consumer)} instead, to consciously deal with any
     * exceptions with the {@code errorConsumer}.
     */
    @Deprecated
    default void handle(Consumer<? super I> initialResultConsumer, Consumer<? super U> updateConsumer) {
        handle(initialResultConsumer, updateConsumer,
               error -> logger.warn("Failed handle the initial result or an update", error));
    }

    /**
     * Delegates handling of initial result and incremental updates to the provided consumers.
     * <p>
     * Subscription to the incremental updates is done after the initial result is retrieved and its consumer is
     * invoked. If anything goes wrong during invoking or consuming initial result or incremental updates, subscription
     * is cancelled <em>after</em> invoking the given {@code errorConsumer}.
     *
     * @param initialResultConsumer The {@link Consumer} to be invoked when the initial result is retrieved.
     * @param updateConsumer        The {@link Consumer} to be invoked when incremental updates are emitted.
     * @param errorConsumer         A {@link Consumer} of {@link Exception} used to react to an exception resulting from
     *                              the {@link #initialResult()} or {@link #updates()}.
     */
    default void handle(Consumer<? super I> initialResultConsumer,
                        Consumer<? super U> updateConsumer,
                        Consumer<Throwable> errorConsumer) {
        initialResult().subscribe(
                initialResult -> {
                    try {
                        initialResultConsumer.accept(initialResult);

                        updates().subscribe(
                                update -> {
                                    try {
                                        updateConsumer.accept(update);
                                    } catch (Exception e) {
                                        errorConsumer.accept(e);
                                        cancel();
                                    }
                                },
                                throwable -> {
                                    errorConsumer.accept(throwable);
                                    cancel();
                                }
                        );
                    } catch (Exception e) {
                        errorConsumer.accept(e);
                        cancel();
                    }
                },
                throwable -> {
                    errorConsumer.accept(throwable);
                    cancel();
                }
        );
    }
}
