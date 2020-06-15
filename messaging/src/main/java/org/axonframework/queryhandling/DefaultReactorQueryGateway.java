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

import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.reactive.ReactiveMessageDispatchInterceptor;
import org.axonframework.messaging.reactive.ReactiveResultHandlerInterceptor;
import org.axonframework.messaging.responsetypes.ResponseType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.messaging.GenericMessage.asMessage;

/**
 * Implementation of the {@link ReactorQueryGateway} that uses Project Reactor to achieve reactiveness.
 *
 * @author Milan Savic
 * @since 4.4
 */
public class DefaultReactorQueryGateway implements ReactorQueryGateway {

    private final List<ReactiveMessageDispatchInterceptor<QueryMessage<?, ?>>> dispatchInterceptors;
    private final List<ReactiveResultHandlerInterceptor<QueryMessage<?, ?>, ResultMessage<?>>> resultInterceptors;

    private final QueryBus queryBus;

    /**
     * Creates an instance of {@link DefaultReactorQueryGateway} based on the fields contained in the {@link
     * Builder}.
     * <p>
     * Will assert that the {@link QueryBus} is not {@code null} and throws an {@link AxonConfigurationException} if
     * it is.
     * </p>
     *
     * @param builder the {@link Builder} used to instantiated a {@link DefaultReactorQueryGateway} instance
     */
    protected DefaultReactorQueryGateway(Builder builder) {
        builder.validate();
        this.queryBus = builder.queryBus;
        this.dispatchInterceptors = builder.dispatchInterceptors;
        this.resultInterceptors = builder.resultInterceptors;
    }

    /**
     * Instantiate a Builder to be able to create a {@link DefaultReactorQueryGateway}.
     * <p>
     * The {@code dispatchInterceptors} are defaulted to an empty list.
     * The {@link QueryBus} is a <b>hard requirements</b> and as such should be provided.
     * </p>
     *
     * @return a Builder to be able to create a {@link DefaultReactorQueryGateway}
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

    @Override
    public Registration registerResultHandlerInterceptor(
            ReactiveResultHandlerInterceptor<QueryMessage<?, ?>, ResultMessage<?>> interceptor) {
        resultInterceptors.add(interceptor);
        return () -> resultInterceptors.remove(interceptor);
    }

    @Override
    public <R, Q> Mono<R> query(String queryName, Q query, ResponseType<R> responseType) {
        return Mono.<QueryMessage<?, ?>>fromCallable(() -> new GenericQueryMessage<>(asMessage(query),
                                                                                     queryName,
                                                                                     responseType))
                .transform(this::processDispatchInterceptors)
                .flatMap(this::dispatchQuery)
                .flatMapMany(this::processResultsInterceptors)
                .<R>transform(this::getPayload)
                .next();
    }

    @Override
    public <R, Q> Flux<R> scatterGather(String queryName, Q query, ResponseType<R> responseType, long timeout,
                                        TimeUnit timeUnit) {
        return Mono.<QueryMessage<?, ?>>fromCallable(() -> new GenericQueryMessage<>(asMessage(query),
                                                                                     queryName,
                                                                                     responseType))
                .transform(this::processDispatchInterceptors)
                .flatMap(q -> dispatchScatterGatherQuery(q, timeout, timeUnit))
                .flatMapMany(this::processResultsInterceptors)
                .transform(this::getPayload);
    }

    @Override
    public <Q, I, U> Mono<SubscriptionQueryResult<I, U>> subscriptionQuery(String queryName, Q query,
                                                                           ResponseType<I> initialResponseType,
                                                                           ResponseType<U> updateResponseType,
                                                                           SubscriptionQueryBackpressure backpressure,
                                                                           int updateBufferSize) {

        //noinspection unchecked
        return Mono.<QueryMessage<?, ?>>fromCallable(() -> new GenericSubscriptionQueryMessage<>(query,
                                                                                                 initialResponseType,
                                                                                                 updateResponseType))
                .transform(this::processDispatchInterceptors)
                .map(isq -> (SubscriptionQueryMessage<Q, U, I>) isq)
                .flatMap(isq -> dispatchSubscriptionQuery(isq, backpressure, updateBufferSize))
                .flatMap(processSubscriptionQueryResult());
    }


    private Mono<Tuple2<QueryMessage<?, ?>, Flux<ResultMessage<?>>>> dispatchQuery(QueryMessage<?, ?> queryMessage) {
        Flux<ResultMessage<?>> results = Flux
                .defer(() -> Mono.fromFuture(queryBus.query(queryMessage)));

        return Mono.<QueryMessage<?, ?>>just(queryMessage)
                .zipWith(Mono.just(results));
    }

    private Mono<Tuple2<QueryMessage<?, ?>, Flux<ResultMessage<?>>>> dispatchScatterGatherQuery(
            QueryMessage<?, ?> queryMessage, long timeout, TimeUnit timeUnit) {
        Flux<ResultMessage<?>> results = Flux
                .defer(() -> Flux.fromStream(queryBus.scatterGather(queryMessage,
                                                                    timeout,
                                                                    timeUnit)));

        return Mono.<QueryMessage<?, ?>>just(queryMessage)
                .zipWith(Mono.just(results));
    }

    private <Q, I, U> Mono<Tuple2<QueryMessage<Q, I>, Mono<SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>>>>> dispatchSubscriptionQuery(
            SubscriptionQueryMessage<Q, I, U> queryMessage, SubscriptionQueryBackpressure backpressure,
            int updateBufferSize) {
        Mono<SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>>> result = Mono
                .fromCallable(() -> queryBus.subscriptionQuery(queryMessage, backpressure, updateBufferSize));
        return Mono.<QueryMessage<Q, I>>just(queryMessage)
                .zipWith(Mono.just(result));
    }

    private Mono<QueryMessage<?, ?>> processDispatchInterceptors(Mono<QueryMessage<?, ?>> queryMessageMono) {
        return Flux.fromIterable(dispatchInterceptors)
                   .reduce(queryMessageMono, (queryMessage, interceptor) -> interceptor.intercept(queryMessage))
                   .flatMap(Mono::from);
    }

    private Flux<ResultMessage<?>> processResultsInterceptors(
            Tuple2<QueryMessage<?, ?>, Flux<ResultMessage<?>>> queryWithResponses) {
        QueryMessage<?, ?> queryMessage = queryWithResponses.getT1();
        Flux<ResultMessage<?>> queryResultMessage = queryWithResponses.getT2();

        return Flux.fromIterable(resultInterceptors)
                   .reduce(queryResultMessage,
                           (result, interceptor) -> interceptor.intercept(queryMessage, result))
                   .flatMapMany(it -> it);
    }

    private <Q, I, U> Function<Tuple2<QueryMessage<Q, U>,
            Mono<SubscriptionQueryResult<QueryResponseMessage<U>, SubscriptionQueryUpdateMessage<I>>>>,
            Mono<SubscriptionQueryResult<I, U>>> processSubscriptionQueryResult() {

        return messageWithResult -> messageWithResult.getT2().map(sqr -> {
            Mono<I> interceptedInitialResult = Mono.<QueryMessage<?, ?>>just(messageWithResult.getT1())
                    .zipWith(Mono.just(Flux.<ResultMessage<?>>from(sqr.initialResult())))
                    .flatMapMany(this::processResultsInterceptors)
                    .<I>transform(this::getPayload)
                    .next();

            Flux<U> interceptedUpdates = Mono.<QueryMessage<?, ?>>just(messageWithResult.getT1())
                    .zipWith(Mono.just(sqr.updates().<ResultMessage<?>>map(it -> it)))
                    .flatMapMany(this::processResultsInterceptors)
                    .transform(this::getPayload);

            return new DefaultSubscriptionQueryResult<>(interceptedInitialResult, interceptedUpdates, sqr);
        });
    }

    @SuppressWarnings("unchecked")
    private <R> Flux<R> getPayload(Flux<ResultMessage<?>> resultMessageFlux) {
        return resultMessageFlux
                .flatMap(this::mapResult)
                .filter(r -> Objects.nonNull(r.getPayload()))
                .map(it -> (R) it.getPayload());
    }

    private Flux<? extends ResultMessage<?>> mapResult(ResultMessage<?> response) {
        return response.isExceptional() ? Flux.error(response.exceptionResult()) : Flux.just(response);
    }

    /**
     * Builder class to instantiate {@link DefaultReactorQueryGateway}.
     * <p>
     * The {@code dispatchInterceptors} are defaulted to an empty list.
     * The {@link QueryBus} is a <b>hard requirement</b> and as such should be provided.
     * </p>
     */
    public static class Builder {

        private QueryBus queryBus;
        private List<ReactiveMessageDispatchInterceptor<QueryMessage<?, ?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();
        private List<ReactiveResultHandlerInterceptor<QueryMessage<?, ?>, ResultMessage<?>>> resultInterceptors = new CopyOnWriteArrayList<>();

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
         * Sets the {@link List} of {@link ReactiveResultHandlerInterceptor}s for {@link CommandResultMessage}s.
         * Are invoked when a result has been received.
         *
         * @param resultInterceptors which are invoked when a result has been received
         * @return the current Builder instance, for fluent interfacing
         */
        @SafeVarargs
        public final Builder resultInterceptors(
                ReactiveResultHandlerInterceptor<QueryMessage<?, ?>, ResultMessage<?>>... resultInterceptors) {
            return resultInterceptors(asList(resultInterceptors));
        }

        /**
         * Sets the {@link List} of {@link ReactiveResultHandlerInterceptor}s for {@link CommandResultMessage}s.
         * Are invoked when a result has been received.
         *
         * @param resultInterceptors which are invoked when a result has been received
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder resultInterceptors(
                List<ReactiveResultHandlerInterceptor<QueryMessage<?, ?>, ResultMessage<?>>> resultInterceptors) {
            this.resultInterceptors = resultInterceptors != null && resultInterceptors.isEmpty()
                    ? new CopyOnWriteArrayList<>(resultInterceptors)
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
         * Initializes a {@link DefaultReactorQueryGateway} as specified through this Builder.
         *
         * @return a {@link DefaultReactorQueryGateway} as specified through this Builder
         */
        public DefaultReactorQueryGateway build() {
            return new DefaultReactorQueryGateway(this);
        }
    }
}
