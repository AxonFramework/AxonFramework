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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.responsetypes.ResponseType;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.queryhandling.GenericQueryResponseMessage.asResponseMessage;

/**
 * Implementation of the QueryGateway interface that allows the registration of dispatchInterceptors.
 *
 * @author Marc Gathier
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.1
 */
public class DefaultQueryGateway implements QueryGateway {

    private final QueryBus queryBus;
    private final List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors;

    /**
     * Instantiate a {@link DefaultQueryGateway} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link QueryBus} is not {@code null}, and will throw an {@link AxonConfigurationException}
     * if it is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link DefaultQueryGateway} instance
     */
    protected DefaultQueryGateway(Builder builder) {
        builder.validate();
        this.queryBus = builder.queryBus;
        this.dispatchInterceptors = builder.dispatchInterceptors;
    }

    /**
     * Instantiate a Builder to be able to create a {@link DefaultQueryGateway}.
     * <p>
     * The {@code dispatchInterceptors} is defaulted to an empty list. The {@link QueryBus} is a
     * <b>hard requirement</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link DefaultQueryGateway}
     */
    public static Builder builder() {
        return new Builder();
    }


    @Override
    public <R, Q> CompletableFuture<R> query(String queryName, Q query, ResponseType<R> responseType) {
        CompletableFuture<QueryResponseMessage<R>> queryResponse = queryBus
                .query(processInterceptors(new GenericQueryMessage<>(query, queryName, responseType)));
        CompletableFuture<R> result = new CompletableFuture<>();
        queryResponse.exceptionally(cause -> asResponseMessage(responseType.responseMessagePayloadType(), cause))
                     .thenAccept(queryResponseMessage -> {
                         if (queryResponseMessage.isExceptional()) {
                             result.completeExceptionally(queryResponseMessage.exceptionResult());
                         } else {
                             result.complete(queryResponseMessage.getPayload());
                         }
                     });
        return result;
    }

    @Override
    public <R, Q> Stream<R> scatterGather(String queryName, Q query, ResponseType<R> responseType, long timeout,
                                          TimeUnit timeUnit) {
        GenericQueryMessage<Q, R> queryMessage = new GenericQueryMessage<>(query, queryName, responseType);
        return queryBus.scatterGather(processInterceptors(queryMessage), timeout, timeUnit)
                       .map(QueryResponseMessage::getPayload);
    }

    @Override
    public <Q, I, U> SubscriptionQueryResult<I, U> subscriptionQuery(String queryName, Q query,
                                                                     ResponseType<I> initialResponseType,
                                                                     ResponseType<U> updateResponseType,
                                                                     SubscriptionQueryBackpressure backpressure,
                                                                     int updateBufferSize) {
        SubscriptionQueryMessage<Q, I, U> subscriptionQueryMessage =
                new GenericSubscriptionQueryMessage<>(query, queryName, initialResponseType, updateResponseType);
        SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> result = queryBus
                .subscriptionQuery(processInterceptors(subscriptionQueryMessage), backpressure, updateBufferSize);
        return new DefaultSubscriptionQueryResult<>(
                result.initialResult()
                      .filter(initialResult -> Objects.nonNull(initialResult.getPayload()))
                      .map(QueryResponseMessage::getPayload),
                result.updates()
                      .filter(update -> Objects.nonNull(update.getPayload()))
                      .map(SubscriptionQueryUpdateMessage::getPayload),
                result
        );
    }

    @Override
    public Registration registerDispatchInterceptor(
            MessageDispatchInterceptor<? super QueryMessage<?, ?>> interceptor) {
        dispatchInterceptors.add(interceptor);
        return () -> dispatchInterceptors.remove(interceptor);
    }

    @SuppressWarnings("unchecked")
    private <Q, R, T extends QueryMessage<Q, R>> T processInterceptors(T query) {
        T intercepted = query;
        for (MessageDispatchInterceptor<? super QueryMessage<?, ?>> interceptor : dispatchInterceptors) {
            intercepted = (T) interceptor.handle(intercepted);
        }
        return intercepted;
    }

    /**
     * Builder class to instantiate a {@link DefaultQueryGateway}.
     * <p>
     * The {@code dispatchInterceptors} is defaulted to an empty list. The {@link QueryBus} is a
     * <b>hard requirement</b> and as such should be provided.
     */
    public static class Builder {

        private QueryBus queryBus;
        private List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors =
                new CopyOnWriteArrayList<>();

        /**
         * Sets the {@link QueryBus} to deliver {@link QueryMessage}s on received in this {@link QueryGateway}
         * implementation.
         *
         * @param queryBus a {@link QueryBus} to deliver {@link QueryMessage}s on received in this {@link QueryGateway}
         *                 implementation
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder queryBus(QueryBus queryBus) {
            assertNonNull(queryBus, "QueryBus may not be null");
            this.queryBus = queryBus;
            return this;
        }

        /**
         * Sets the {@link List} of {@link MessageDispatchInterceptor}s for {@link QueryMessage}s.
         * Are invoked when a query is being dispatched.
         *
         * @param dispatchInterceptors which are invoked when a query is being dispatched
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder dispatchInterceptors(
                MessageDispatchInterceptor<? super QueryMessage<?, ?>>... dispatchInterceptors) {
            return dispatchInterceptors(asList(dispatchInterceptors));
        }

        /**
         * Sets the {@link List} of {@link MessageDispatchInterceptor}s for {@link QueryMessage}s.
         * Are invoked when a query is being dispatched.
         *
         * @param dispatchInterceptors which are invoked when a query is being dispatched
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder dispatchInterceptors(
                List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors) {
            this.dispatchInterceptors = dispatchInterceptors != null && !dispatchInterceptors.isEmpty()
                    ? new CopyOnWriteArrayList<>(dispatchInterceptors)
                    : new CopyOnWriteArrayList<>();
            return this;
        }

        /**
         * Initializes a {@link DefaultQueryGateway} as specified through this Builder.
         *
         * @return a {@link DefaultQueryGateway} as specified through this Builder
         */
        public DefaultQueryGateway build() {
            return new DefaultQueryGateway(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(queryBus, "The QueryBus is a hard requirement and should be provided");
        }
    }
}
