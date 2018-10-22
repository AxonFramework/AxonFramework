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

import org.axonframework.messaging.MessageDispatchInterceptorSupport;

import java.util.function.Predicate;

/**
 * Component which informs subscription queries about updates, errors and when there are no more updates.
 * <p>
 * If any of the emitter functions in this interface are called from a message handling function (e.g. an {@link
 * org.axonframework.eventhandling.EventHandler} annotated function), then that call will automatically be tied into the
 * lifecycle of the current {@link org.axonframework.messaging.unitofwork.UnitOfWork} to ensure correct order of
 * execution.
 * <p>
 * Added, implementations of this class should thus respect any current UnitOfWork in the
 * {@link org.axonframework.messaging.unitofwork.UnitOfWork.Phase#STARTED} phase for any of the emitting functions. If
 * this is the case then the emitter call action should be performed during the
 * {@link org.axonframework.messaging.unitofwork.UnitOfWork.Phase#AFTER_COMMIT}. Otherwise the operation can be executed
 * immediately.
 *
 * @author Milan Savic
 * @since 3.3
 */
public interface QueryUpdateEmitter extends MessageDispatchInterceptorSupport<SubscriptionQueryUpdateMessage<?>> {

    /**
     * Emits incremental update (as return value of provided update function) to subscription queries matching given
     * filter.
     *
     * @param filter predicate on subscription query message used to filter subscription queries
     * @param update incremental update message
     * @param <U>    the type of the update
     */
    <U> void emit(Predicate<SubscriptionQueryMessage<?, ?, U>> filter, SubscriptionQueryUpdateMessage<U> update);

    /**
     * Emits given incremental update to subscription queries matching given filter. If an {@code update} is {@code
     * null}, emit will be skipped. In order to send nullable updates, use {@link #emit(Class, Predicate,
     * SubscriptionQueryUpdateMessage)} or {@link #emit(Predicate, SubscriptionQueryUpdateMessage)} methods.
     *
     * @param filter predicate on subscription query message used to filter subscription queries
     * @param update incremental update
     * @param <U>    the type of the update
     */
    default <U> void emit(Predicate<SubscriptionQueryMessage<?, ?, U>> filter, U update) {
        if (update != null) {
            emit(filter, GenericSubscriptionQueryUpdateMessage.asUpdateMessage(update));
        }
    }

    /**
     * Emits given incremental update to subscription queries matching given query type and filter.
     *
     * @param queryType the type of the query
     * @param filter    predicate on query payload used to filter subscription queries
     * @param update    incremental update message
     * @param <Q>       the type of the query
     * @param <U>       the type of the update
     */
    @SuppressWarnings("unchecked")
    default <Q, U> void emit(Class<Q> queryType, Predicate<? super Q> filter,
                             SubscriptionQueryUpdateMessage<U> update) {
        Predicate<SubscriptionQueryMessage<?, ?, U>> sqmFilter =
                m -> queryType.isAssignableFrom(m.getPayloadType()) && filter.test((Q) m.getPayload());
        emit(sqmFilter, update);
    }

    /**
     * Emits given incremental update to subscription queries matching given query type and filter. If an {@code update}
     * is {@code null}, emit will be skipped. In order to send nullable updates, use {@link #emit(Class, Predicate,
     * SubscriptionQueryUpdateMessage)} or {@link #emit(Predicate, SubscriptionQueryUpdateMessage)} methods.
     *
     * @param queryType the type of the query
     * @param filter    predicate on query payload used to filter subscription queries
     * @param update    incremental update
     * @param <Q>       the type of the query
     * @param <U>       the type of the update
     */
    default <Q, U> void emit(Class<Q> queryType, Predicate<? super Q> filter, U update) {
        if (update != null) {
            emit(queryType, filter, GenericSubscriptionQueryUpdateMessage.asUpdateMessage(update));
        }
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
    default <Q> void complete(Class<Q> queryType, Predicate<? super Q> filter) {
        Predicate<SubscriptionQueryMessage<?, ?, ?>> sqmFilter =
                m -> queryType.isAssignableFrom(m.getPayloadType()) && filter.test((Q) m.getPayload());
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
    default <Q> void completeExceptionally(Class<Q> queryType, Predicate<? super Q> filter, Throwable cause) {
        Predicate<SubscriptionQueryMessage<?, ?, ?>> sqmFilter =
                m -> queryType.isAssignableFrom(m.getPayloadType()) && filter.test((Q) m.getPayload());
        completeExceptionally(sqmFilter, cause);
    }

    /**
     * Checks whether there is a query update handler for a given {@code query}.
     *
     * @param query the subscription query for which we have registered the update handler
     * @return {@code true} if there is an update handler registered for given {@code query}, {@code false} otherwise
     */
    boolean queryUpdateHandlerRegistered(SubscriptionQueryMessage<?, ?, ?> query);

    /**
     * Registers an Update Handler for given {@code query} with given {@code backpressure} and {@code updateBufferSize}.
     *
     * @param query            the subscription query for which we register an Update Handler
     * @param backpressure     the backpressure mechanism to be used for emitting updates
     * @param updateBufferSize the size of buffer which accumulates updates before subscription to the {@code flux} is
     *                         made
     * @param <U>              the incremental response types of the query
     * @return the object which contains updates and a registration which can be used to cancel them
     */
    <U> UpdateHandlerRegistration<U> registerUpdateHandler(SubscriptionQueryMessage<?, ?, ?> query,
                                                           SubscriptionQueryBackpressure backpressure,
                                                           int updateBufferSize);
}
