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
import org.axonframework.messaging.ReactiveMessageDispatchInterceptor;
import org.axonframework.messaging.ReactiveResultHandlerInterceptor;
import org.axonframework.messaging.responsetypes.ResponseType;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.Duration;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.messaging.GenericMessage.asMessage;

/**
 * Implementation of the {@link ReactiveQueryGateway} that uses Project Reactor to achieve reactiveness.
 *
 * @author Milan Savic
 * @since 4.4
 */
public class ReactorQueryGateway implements ReactiveQueryGateway {

    private final List<ReactiveMessageDispatchInterceptor<QueryMessage<?, ?>>> dispatchInterceptors;
    private final List<ReactiveResultHandlerInterceptor<QueryMessage<?, ?>, QueryResponseMessage<?>>> resultInterceptors;

    private final QueryBus queryBus;

    /**
     * Creates an instance of {@link ReactorQueryGateway} based on the fields contained in the {@link
     * Builder}.
     * <p>
     * Will assert that the {@link QueryBus} is not {@code null} and throws an {@link AxonConfigurationException} if
     * it is.
     * </p>
     *
     * @param builder the {@link Builder} used to instantiated a {@link ReactorQueryGateway} instance
     */
    protected ReactorQueryGateway(Builder builder) {
        builder.validate();
        this.queryBus = builder.queryBus;
        this.dispatchInterceptors = builder.dispatchInterceptors;
        this.resultInterceptors = builder.resultInterceptors;
    }

    /**
     * Instantiate a Builder to be able to create a {@link ReactorQueryGateway}.
     * <p>
     * The {@code dispatchInterceptors} are defaulted to an empty list.
     * The {@link QueryBus} is a <b>hard requirements</b> and as such should be provided.
     * </p>
     *
     * @return a Builder to be able to create a {@link ReactorQueryGateway}
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
            ReactiveResultHandlerInterceptor<QueryMessage<?, ?>, QueryResponseMessage<?>> interceptor) {
        resultInterceptors.add(interceptor);
        return () -> resultInterceptors.remove(interceptor);
    }

    @Override
    public <R, Q> Mono<R> query(String queryName, Q query, ResponseType<R> responseType) {
        return Mono.<QueryMessage<?, ?>>fromCallable(() -> new GenericQueryMessage<>(asMessage(query), queryName, responseType)) //TODO write tests for retry
                .transform(this::processQueryInterceptors)
                .flatMap(this::dispatchQuery)
                .flatMapMany(this::processResultsInterceptors)
                .map(it -> (R) it.getPayload())
                .next();
    }

    private Mono<QueryMessage<?, ?>> processQueryInterceptors(Mono<QueryMessage<?, ?>> queryMessageMono) {
        return Flux.fromIterable(dispatchInterceptors)
                .reduce(queryMessageMono, (queryMessage, interceptor) -> interceptor.intercept(queryMessage))
                .flatMap(Mono::from);
    }

    private Mono<Tuple2<QueryMessage<?, ?>, Flux<QueryResponseMessage<?>>>> dispatchQuery(QueryMessage<?, ?> queryMessage) {
        Flux<QueryResponseMessage<?>> results = Flux.defer(() -> Mono.<QueryResponseMessage<?>>fromFuture(queryBus.query(queryMessage)))
                .flatMap(this::mapResult);

        return Mono.<QueryMessage<?, ?>>just(queryMessage)
                .zipWith(Mono.just(results));
    }

    private Flux<? extends QueryResponseMessage<?>> mapResult(QueryResponseMessage<?> it) {
        return it.isExceptional() ? Flux.error(it.exceptionResult()) : Flux.just(it);
    }

    private Flux<? extends QueryResponseMessage<?>> processResultsInterceptors(Tuple2<QueryMessage<?, ?>, Flux<QueryResponseMessage<?>>> tuple2) {
        QueryMessage<?, ?> queryMessage = tuple2.getT1();
        Flux<QueryResponseMessage<?>> queryResultMessage = tuple2.getT2();

        return Flux.fromIterable(resultInterceptors)
                .reduce(queryResultMessage,
                        (result, interceptor) -> interceptor.intercept(queryMessage, result))
                .flatMapMany(it -> it);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, Q> Flux<R> scatterGather(String queryName, Q query, ResponseType<R> responseType, long timeout,
                                        TimeUnit timeUnit) {
        return Mono.<QueryMessage<?, ?>>fromCallable(() -> new GenericQueryMessage<>(asMessage(query), queryName, responseType)) //TODO write tests for retry
                .transform(this::processQueryInterceptors)
                .flatMap(q -> dispatchScatterGatherQuery(q, timeout, timeUnit))
                .flatMapMany(this::processResultsInterceptors)
                .map(it -> (R) it.getPayload());
    }

    private Mono<Tuple2<QueryMessage<?, ?>, Flux<QueryResponseMessage<?>>>> dispatchScatterGatherQuery(QueryMessage<?, ?> queryMessage, long timeout, TimeUnit timeUnit) {
        Flux<QueryResponseMessage<?>> results = Flux.defer(() -> Flux.<QueryResponseMessage<?>>fromStream(queryBus.scatterGather(queryMessage, timeout, timeUnit)))
                .flatMap(this::mapResult);

        return Mono.<QueryMessage<?, ?>>just(queryMessage)
                .zipWith(Mono.just(results));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <Q, I, U> Mono<SubscriptionQueryResult<I, U>> subscriptionQuery(String queryName, Q query,
                                                                           ResponseType<I> initialResponseType,
                                                                           ResponseType<U> updateResponseType,
                                                                           SubscriptionQueryBackpressure backpressure,
                                                                           int updateBufferSize) {
        return null; //TODO
    }

    /**
     * Builder class to instantiate {@link ReactorQueryGateway}.
     * <p>
     * The {@code dispatchInterceptors} are defaulted to an empty list.
     * The {@link QueryBus} is a <b>hard requirement</b> and as such should be provided.
     * </p>
     */
    public static class Builder {

        private QueryBus queryBus;
        private List<ReactiveMessageDispatchInterceptor<QueryMessage<?, ?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();
        private List<ReactiveResultHandlerInterceptor<QueryMessage<?, ?>, QueryResponseMessage<?>>> resultInterceptors = new CopyOnWriteArrayList<>();

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
                ReactiveResultHandlerInterceptor<QueryMessage<?, ?>, QueryResponseMessage<?>>... resultInterceptors) {
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
                List<ReactiveResultHandlerInterceptor<QueryMessage<?, ?>, QueryResponseMessage<?>>> resultInterceptors) {
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
         * Initializes a {@link ReactorQueryGateway} as specified through this Builder.
         *
         * @return a {@link ReactorQueryGateway} as specified through this Builder
         */
        public ReactorQueryGateway build() {
            return new ReactorQueryGateway(this);
        }
    }
}
