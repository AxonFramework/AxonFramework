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

import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptorSupport;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;

import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Component which informs subscription queries about updates, errors and when there are no more updates.
 * <p>
 * If any of the emitter functions in this interface are called from a message handling function (e.g. an {@link
 * EventHandler} annotated function), then that call will automatically be tied into the
 * lifecycle of the current {@link LegacyUnitOfWork} to ensure correct order of
 * execution.
 * <p>
 * Added, implementations of this class should thus respect any current UnitOfWork in the {@link
 * LegacyUnitOfWork.Phase#STARTED} phase for any of the emitting functions. If this is
 * the case then the emitter call action should be performed during the {@link LegacyUnitOfWork.Phase#AFTER_COMMIT}.
 * Otherwise the operation can be executed immediately.
 *
 * @author Milan Savic
 * @since 3.3
 */
public interface QueryUpdateEmitter extends MessageDispatchInterceptorSupport<SubscriptionQueryUpdateMessage> {

    /**
     * Emits incremental update (as return value of provided update function) to subscription queries matching given
     * filter.
     *
     * @param filter predicate on subscription query message used to filter subscription queries
     * @param update incremental update message
     * @param <U>    the type of the update
     */
    <U> void emit(@Nonnull Predicate<SubscriptionQueryMessage<?, ?, U>> filter,
                  @Nonnull SubscriptionQueryUpdateMessage update);

    /**
     * Emits given incremental update to subscription queries matching given filter. If an {@code update} is {@code
     * null}, emit will be skipped. In order to send nullable updates, use {@link #emit(Class, Predicate,
     * SubscriptionQueryUpdateMessage)} or {@link #emit(Predicate, SubscriptionQueryUpdateMessage)} methods.
     *
     * @param filter predicate on subscription query message used to filter subscription queries
     * @param update incremental update
     * @param <U>    the type of the update
     */
    default <U> void emit(@Nonnull Predicate<SubscriptionQueryMessage<?, ?, U>> filter, @Nullable U update) {
        if (update != null) {
            emit(filter, asUpdateMessage(update));
        }
    }

    /**
     * Creates {@link GenericSubscriptionQueryUpdateMessage} from provided {@code payload} which represents incremental
     * update. The provided {@code payload} may not be {@code null}.
     *
     * @param payload incremental update
     * @return created a {@link SubscriptionQueryUpdateMessage} with the given {@code payload}.
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link QualifiedName name}.
     */
    @Deprecated
    private static SubscriptionQueryUpdateMessage asUpdateMessage(Object payload) {
        if (payload instanceof SubscriptionQueryUpdateMessage squm) {
            return squm;
        }
        if (payload instanceof ResultMessage resultMessage) {
            if (resultMessage.isExceptional()) {
                Throwable cause = resultMessage.exceptionResult();
                return new GenericSubscriptionQueryUpdateMessage(
                        new MessageType(cause.getClass()),
                        cause,
                        resultMessage.payloadType(),
                        resultMessage.metaData()
                );
            }
            return new GenericSubscriptionQueryUpdateMessage(resultMessage);
        }
        if (payload instanceof Message message) {
            return new GenericSubscriptionQueryUpdateMessage(message);
        }
        // TODO #3085 use MessageNameResolver below
        return new GenericSubscriptionQueryUpdateMessage(new MessageType(payload.getClass()), payload);
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
    default <Q, U> void emit(@Nonnull Class<Q> queryType,
                             @Nonnull Predicate<? super Q> filter,
                             @Nonnull SubscriptionQueryUpdateMessage update) {
        Predicate<SubscriptionQueryMessage<?, ?, U>> sqmFilter =
                m -> queryType.isAssignableFrom(m.payloadType()) && filter.test((Q) m.payload());
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
    default <Q, U> void emit(@Nonnull Class<Q> queryType, @Nonnull Predicate<? super Q> filter, @Nullable U update) {
        if (update != null) {
            emit(queryType, filter, asUpdateMessage(update));
        }
    }

    /**
     * Completes subscription queries matching given filter.
     *
     * @param filter predicate on subscription query message used to filter subscription queries
     */
    void complete(@Nonnull Predicate<SubscriptionQueryMessage<?, ?, ?>> filter);

    /**
     * Completes subscription queries matching given query type and filter.
     *
     * @param queryType the type of the query
     * @param filter    predicate on query payload used to filter subscription queries
     * @param <Q>       the type of the query
     */
    @SuppressWarnings("unchecked")
    default <Q> void complete(@Nonnull Class<Q> queryType, @Nonnull Predicate<? super Q> filter) {
        Predicate<SubscriptionQueryMessage<?, ?, ?>> sqmFilter =
                m -> queryType.isAssignableFrom(m.payloadType()) && filter.test((Q) m.payload());
        complete(sqmFilter);
    }

    /**
     * Completes with an error subscription queries matching given filter.
     *
     * @param filter predicate on subscription query message used to filter subscription queries
     * @param cause  the cause of an error
     */
    void completeExceptionally(@Nonnull Predicate<SubscriptionQueryMessage<?, ?, ?>> filter, @Nonnull Throwable cause);

    /**
     * Completes with an error subscription queries matching given query type and filter
     *
     * @param queryType the type of the query
     * @param filter    predicate on query payload used to filter subscription queries
     * @param cause     the cause of an error
     * @param <Q>       the type of the query
     */
    @SuppressWarnings("unchecked")
    default <Q> void completeExceptionally(@Nonnull Class<Q> queryType, @Nonnull Predicate<? super Q> filter,
                                           @Nonnull Throwable cause) {
        Predicate<SubscriptionQueryMessage<?, ?, ?>> sqmFilter =
                m -> queryType.isAssignableFrom(m.payloadType()) && filter.test((Q) m.payload());
        completeExceptionally(sqmFilter, cause);
    }

    /**
     * Checks whether there is a query update handler for a given {@code query}.
     *
     * @param query the subscription query for which we have registered the update handler
     * @return {@code true} if there is an update handler registered for given {@code query}, {@code false} otherwise
     */
    boolean queryUpdateHandlerRegistered(@Nonnull SubscriptionQueryMessage<?, ?, ?> query);

    /**
     * Registers an Update Handler for given {@code query} with given {@code updateBufferSize}.
     *
     * @param query            the subscription query for which we register an Update Handler
     * @param updateBufferSize the size of buffer which accumulates updates before subscription to the {@code flux} is
     *                         made
     * @param <U>              the incremental response types of the query
     * @return the object which contains updates and a registration which can be used to cancel them
     */
    <U> UpdateHandlerRegistration registerUpdateHandler(@Nonnull SubscriptionQueryMessage<?, ?, ?> query,
                                                           int updateBufferSize);

    /**
     * Provides the set of running subscription queries. If there are changes to subscriptions they will be reflected in
     * the returned set of this method. Implementations should provide an unmodifiable set of the active subscriptions.
     *
     * @return the set of running subscription queries
     */
    default Set<SubscriptionQueryMessage<?, ?, ?>> activeSubscriptions() {
        return Collections.emptySet();
    }
}
