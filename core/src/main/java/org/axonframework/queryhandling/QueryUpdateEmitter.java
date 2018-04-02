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

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Component which informs subscription queries about updates, errors and when there are no more updates.
 *
 * @author Milan Savic
 * @see UpdateHandler
 * @since 3.3
 */
public interface QueryUpdateEmitter {

    /**
     * Emits incremental update (as return value of provided update function) to subscription queries matching given
     * filter.
     *
     * @param filter predicate on subscription query message used to filter subscription queries
     * @param update function which returns incremental update
     * @param <U>    the type of the update
     */
    <U> void emit(Predicate<SubscriptionQueryMessage<?, ?, U>> filter,
                  Function<SubscriptionQueryMessage<?, ?, U>, U> update);

    /**
     * Emits given incremental update to subscription queries matching given filter.
     *
     * @param filter predicate on subscription query message used to filter subscription queries
     * @param update incremental update
     * @param <U>    the type of the update
     */
    default <U> void emit(Predicate<SubscriptionQueryMessage<?, ?, U>> filter, U update) {
        emit(filter, (Function<SubscriptionQueryMessage<?, ?, U>, U>) q -> update);
    }

    /**
     * Emits incremental update (as return value of provided update function) to subscription queries matching given
     * query type and filter.
     *
     * @param queryType the type of the query
     * @param filter    predicate on query payload used to filter subscription queries
     * @param update    function which returns incremental update
     * @param <Q>       the type of the query
     * @param <U>       the type of the update
     */
    @SuppressWarnings("unchecked")
    default <Q, U> void emit(Class<Q> queryType, Predicate<Q> filter, Function<Q, U> update) {
        Predicate<SubscriptionQueryMessage<?, ?, U>> sqmFilter =
                m -> m.getPayloadType().equals(queryType) && filter.test((Q) m.getPayload());

        Function<SubscriptionQueryMessage<?, ?, U>, U> sqmUpdate = q -> update.apply((Q) q.getPayload());

        emit(sqmFilter, sqmUpdate);
    }

    /**
     * Emits given incremental update to subscription queries matching given query type and filter.
     *
     * @param queryType the type of the query
     * @param filter    predicate on query payload used to filter subscription queries
     * @param update    incremental update
     * @param <Q>       the type of the query
     * @param <U>       the type of the update
     */
    default <Q, U> void emit(Class<Q> queryType, Predicate<Q> filter, U update) {
        emit(queryType, filter, q -> update);
    }

    /**
     * Completes subscription queries matching given filter.
     *
     * @param filter predicate on subscription query message used to filter subscription queries
     */
    void complete(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter);

    /**
     * Completes subscription queries matching given query type and filter.
     *
     * @param queryType the type of the query
     * @param filter    predicate on query payload used to filter subscription queries
     * @param <Q>       the type of the query
     */
    @SuppressWarnings("unchecked")
    default <Q> void complete(Class<Q> queryType, Predicate<Q> filter) {
        Predicate<SubscriptionQueryMessage<?, ?, ?>> sqmFilter =
                m -> m.getPayloadType().equals(queryType) && filter.test((Q) m.getPayload());
        complete(sqmFilter);
    }

    /**
     * Completes with an error subscription queries matching given filter.
     *
     * @param filter predicate on subscription query message used to filter subscription queries
     * @param cause  the cause of an error
     */
    void completeExceptionally(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter, Throwable cause);

    /**
     * Completes with an error subscription queries matching given query type and filter
     *
     * @param queryType the type of the query
     * @param filter    predicate on query payload used to filter subscription queries
     * @param cause     the cause of an error
     * @param <Q>       the type of the query
     */
    @SuppressWarnings("unchecked")
    default <Q> void completeExceptionally(Class<Q> queryType, Predicate<Q> filter, Throwable cause) {
        Predicate<SubscriptionQueryMessage<?, ?, ?>> sqmFilter =
                m -> m.getPayloadType().equals(queryType) && filter.test((Q) m.getPayload());
        completeExceptionally(sqmFilter, cause);
    }
}
