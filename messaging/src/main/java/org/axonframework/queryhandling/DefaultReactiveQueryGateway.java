/*
 * Copyright (c) 2010-2020. Axon Framework
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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.ReactiveMessageDispatchInterceptor;
import org.axonframework.messaging.responsetypes.ResponseType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Default implementation of the {@link ReactiveQueryGateway}.
 *
 * @author Milan Savic
 * @since 4.4
 */
public class DefaultReactiveQueryGateway implements ReactiveQueryGateway {

    private final List<ReactiveMessageDispatchInterceptor<QueryMessage<?, ?>>> dispatchInterceptors;

    private final QueryBus queryBus;

    /**
     * Creates an instance of {@link DefaultReactiveQueryGateway} based on the fields contained in the {@link
     * Builder}.
     * <p>
     * Will assert that the {@link QueryBus} is not {@code null} and throws an {@link AxonConfigurationException} if
     * it is.
     * </p>
     *
     * @param builder the {@link Builder} used to instantiated a {@link DefaultReactiveQueryGateway} instance
     */
    protected DefaultReactiveQueryGateway(Builder builder) {
        builder.validate();
        this.queryBus = builder.queryBus;
        this.dispatchInterceptors = builder.dispatchInterceptors;
    }

    /**
     * Instantiate a Builder to be able to create a {@link DefaultReactiveQueryGateway}.
     * <p>
     * The {@code dispatchInterceptors} are defaulted to an empty list.
     * The {@link QueryBus} is a <b>hard requirements</b> and as such should be provided.
     * </p>
     *
     * @return a Builder to be able to create a {@link DefaultReactiveQueryGateway}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Registration registerDispatchInterceptor(
            ReactiveMessageDispatchInterceptor<QueryMessage<?, ?>> interceptor) {
        dispatchInterceptors.add(interceptor);
        return () -> dispatchInterceptors.remove(interceptor);
    }

    private Mono<QueryMessage<?, ?>> processInterceptors(Mono<QueryMessage<?, ?>> queryMessage) {
        Mono<QueryMessage<?, ?>> message = queryMessage;
        for (ReactiveMessageDispatchInterceptor<QueryMessage<?, ?>> dispatchInterceptor : dispatchInterceptors) {
            try {
                message = dispatchInterceptor.intercept(message);
            } catch (Throwable t) {
                return Mono.error(t);
            }
        }
        return message;
    }

    @Override
    public <R, Q> Mono<R> query(String queryName, Mono<Q> query, ResponseType<R> responseType) {
        return processInterceptors(query.map(q -> new GenericQueryMessage<>(q, queryName, responseType)))
                .flatMap(queryMessage -> Mono.create(
                        sink -> queryBus.query(queryMessage).whenComplete((response, throwable) -> {
                            try {
                                if (throwable != null) {
                                    sink.error(throwable);
                                } else {
                                    if (response.isExceptional()) {
                                        sink.error(response.exceptionResult());
                                    } else {
                                        //noinspection unchecked
                                        sink.success(((QueryResponseMessage<R>) response).getPayload());
                                    }
                                }
                            } catch (Exception e) {
                                sink.error(e);
                            }
                        })
                ));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, Q> Flux<R> scatterGather(String queryName, Mono<Q> query, ResponseType<R> responseType, long timeout,
                                        TimeUnit timeUnit) {
        return processInterceptors(query.map(q -> new GenericQueryMessage<>(q, queryName, responseType)))
                .flatMapMany(queryMessage -> Flux.create(
                        sink -> {
                            try {
                                queryBus.scatterGather((QueryMessage<?, R>) queryMessage, timeout, timeUnit)
                                        .forEach(r -> {
                                            if (r.isExceptional()) {
                                                sink.error(r.exceptionResult());
                                            } else {
                                                R payload = r.getPayload();
                                                if (payload != null) {
                                                    sink.next(payload);
                                                }
                                            }
                                        });
                                sink.complete();
                            } catch (Exception e) {
                                sink.error(e);
                            }
                        }
                ));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <Q, I, U> Mono<SubscriptionQueryResult<I, U>> subscriptionQuery(String queryName, Mono<Q> query,
                                                                           ResponseType<I> initialResponseType,
                                                                           ResponseType<U> updateResponseType,
                                                                           SubscriptionQueryBackpressure backpressure,
                                                                           int updateBufferSize) {
        return processInterceptors(query.map(q -> new GenericSubscriptionQueryMessage<>(q,
                                                                                        queryName,
                                                                                        initialResponseType,
                                                                                        updateResponseType)))
                .flatMap(queryMessage -> {
                    SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> data = queryBus
                            .subscriptionQuery((SubscriptionQueryMessage<Q, I, U>) queryMessage,
                                               backpressure,
                                               updateBufferSize);
                    return Mono.just(new DefaultSubscriptionQueryResult<>(
                            data.initialResult()
                                .filter(initialResult -> Objects.nonNull(initialResult.getPayload()))
                                .map(Message::getPayload)
                                .onErrorMap(e -> e instanceof IllegalPayloadAccessException ? e.getCause() : e),
                            data.updates()
                                .filter(update -> Objects.nonNull(update.getPayload()))
                                .map(SubscriptionQueryUpdateMessage::getPayload),
                            data
                    ));
                });
    }

    /**
     * Builder class to instantiate {@link DefaultReactiveQueryGateway}.
     * <p>
     * The {@code dispatchInterceptors} are defaulted to an empty list.
     * The {@link QueryBus} is a <b>hard requirement</b> and as such should be provided.
     * </p>
     */
    public static class Builder {

        private QueryBus queryBus;
        private List<ReactiveMessageDispatchInterceptor<QueryMessage<?, ?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();

        /**
         * Sets the {@link QueryBus} used to dispatch queries.
         *
         * @param queryBus a {@link QueryBus} used to dispatch queries
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder queryBus(QueryBus queryBus) {
            assertNonNull(queryBus, "QueryBus may not be null");
            this.queryBus = queryBus;
            return this;
        }

        /**
         * Sets the {@link List} of {@link ReactiveMessageDispatchInterceptor}s for {@link QueryMessage}s. Are invoked
         * when a query is being dispatched.
         *
         * @param dispatchInterceptors which are invoked when a query is being dispatched
         * @return the current Builder instance, for fluent interfacing
         */
        @SafeVarargs
        public final Builder dispatchInterceptors(
                ReactiveMessageDispatchInterceptor<QueryMessage<?, ?>>... dispatchInterceptors) {
            return dispatchInterceptors(asList(dispatchInterceptors));
        }

        /**
         * Sets the {@link List} of {@link ReactiveMessageDispatchInterceptor}s for {@link QueryMessage}s. Are invoked
         * when a query is being dispatched.
         *
         * @param dispatchInterceptors which are invoked when a query is being dispatched
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder dispatchInterceptors(
                List<ReactiveMessageDispatchInterceptor<QueryMessage<?, ?>>> dispatchInterceptors) {
            this.dispatchInterceptors = dispatchInterceptors != null && dispatchInterceptors.isEmpty()
                    ? new CopyOnWriteArrayList<>(dispatchInterceptors)
                    : new CopyOnWriteArrayList<>();
            return this;
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            assertNonNull(queryBus, "The QueryBus is a hard requirement and should be provided");
        }

        /**
         * Initializes a {@link DefaultReactiveQueryGateway} as specified through this Builder.
         *
         * @return a {@link DefaultReactiveQueryGateway} as specified through this Builder
         */
        public DefaultReactiveQueryGateway build() {
            return new DefaultReactiveQueryGateway(this);
        }
    }
}
