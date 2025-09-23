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
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Container of the {@link #initialResult() initial result} and subsequent {@link #updates() updates} from a
 * {@link QueryGateway#subscriptionQuery(Object, Class, Class, ProcessingContext)}.
 * <p>
 * Until there is a subscription on either of the publishers of this response, nothing happens.
 *
 * @param <I> The type of payload contained in the {@link #initialResult() initial result}.
 * @param <U> The type of payload contained in the {@link #updates()}.
 * @author Milan Savic
 * @author Steven van Beelen
 * @see QueryGateway#subscriptionQuery(Object, Class, Class, ProcessingContext)
 * @see QueryGateway#subscriptionQuery(Object, Class, Class, ProcessingContext, int)
 * @since 3.3.0
 */
public interface SubscriptionQueryResponse<I, U> {

    /**
     * Returns the {@code Flux} of initial results from hitting a {@link QueryHandler}.
     * <p>
     * The {@code QueryHandler} will not be invoked until the resulting {@code Flux} is subscribed to. The return
     * value(s) of the {@code QueryHandler} will be passed to the {@code Flux}, once it's ready.
     *
     * @return The {@code Flux} of initial results from hitting a {@link QueryHandler}.
     */
    @Nonnull
    Flux<I> initialResult();

    /**
     * Returns the {@code Flux} of updates sent to the
     * {@link QueryGateway#subscriptionQuery(Object, Class, Class, ProcessingContext) subscription query}.
     * <p>
     * Updates are typically emitted through the {@link QueryUpdateEmitter#emit(QualifiedName, Predicate, Object)}.
     * <p>
     * When there is an update to the subscription query that resulted in {@code this} response, it will be emitted to
     * the returned {@code Flux}.
     *
     * @return The {@code Flux} of updates sent to the
     * {@link QueryGateway#subscriptionQuery(Object, Class, Class, ProcessingContext) subscription query}.
     */
    @Nonnull
    Flux<U> updates();

    /**
     * Closes this {@code SubscriptionQueryResponse} canceling and/or closing any subscriptions backing the responses
     * returned by {@link #initialResult()} and {@link #updates()}.
     */
    void close();

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
    default void handle(@Nonnull Consumer<? super I> initialResultConsumer,
                        @Nonnull Consumer<? super U> updateConsumer,
                        @Nonnull Consumer<Throwable> errorConsumer) {
        initialResult().subscribe(
                initialResult -> {
                    try {
                        initialResultConsumer.accept(initialResult);
                        updates().subscribe(
                                update -> {
                                    try {
                                        updateConsumer.accept(update);
                                    } catch (Exception e) {
                                        close();
                                        errorConsumer.accept(e);
                                    }
                                },
                                throwable -> {
                                    close();
                                    errorConsumer.accept(throwable);
                                }
                        );
                    } catch (Exception e) {
                        close();
                        errorConsumer.accept(e);
                    }
                },
                throwable -> {
                    close();
                    errorConsumer.accept(throwable);
                }
        );
    }
}
