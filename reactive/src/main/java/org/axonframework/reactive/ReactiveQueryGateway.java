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

package org.axonframework.reactive;

import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.UpdateHandler;
import org.axonframework.queryhandling.backpressure.BackpressuredUpdateHandler;
import org.axonframework.queryhandling.backpressure.NoopBackpressure;
import org.axonframework.queryhandling.responsetypes.ResponseType;
import org.axonframework.queryhandling.responsetypes.ResponseTypes;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Variation of {@link QueryGateway}. Provides support for reactive return types such as {@link Mono} and {@link Flux}
 * from Project Reactor.
 *
 * @author Milan Savic
 * @since 5.0
 */
public interface ReactiveQueryGateway {

    /**
     * Sends given {@code query} over the {@link QueryBus}, expecting a response with the given {@code responseType}
     * from a single source. The query name will be derived from the provided {@code query}. Execution may be
     * asynchronous, depending on the QueryBus implementation.
     *
     * @param query        The {@code query} to be sent
     * @param responseType A {@link Class} describing the desired response type
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link Mono} containing the query result as dictated by the given {@code responseType}
     */
    default <R, Q> Mono<R> query(Q query, Class<R> responseType) {
        return query(query.getClass().getName(), query, responseType);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus}, expecting a response with the given {@code responseType}
     * from a single source. Execution may be asynchronous, depending on the QueryBus implementation.
     *
     * @param queryName    A {@link String} describing the query to be executed
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link Mono} containing the query result as dictated by the given {@code responseType}
     */
    default <R, Q> Mono<R> query(String queryName, Q query, Class<R> responseType) {
        return query(queryName, query, ResponseTypes.instanceOf(responseType));
    }

    /**
     * Sends given {@code query} over the {@link QueryBus}, expecting a response in the form of {@code responseType}
     * from a single source. The query name will be derived from the provided {@code query}. Execution may be
     * asynchronous, depending on the QueryBus implementation.
     *
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link Mono} containing the query result as dictated by the given {@code responseType}
     */
    default <R, Q> Mono<R> query(Q query, ResponseType<R> responseType) {
        return query(query.getClass().getName(), query, responseType);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus}, expecting a response in the form of {@code responseType}
     * from a single source. Execution may be asynchronous, depending on the QueryBus implementation.
     *
     * @param queryName    A {@link String} describing the query to be executed
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link Mono} containing the query result as dictated by the given {@code responseType}
     */
    <R, Q> Mono<R> query(String queryName, Q query, ResponseType<R> responseType);

    /**
     * Sends given {@code query} over the {@link QueryBus}, expecting a response in the form of {@code responseType}
     * from several sources. The flux is completed when a {@code timeout} occurs or when all results are received. The
     * query name will be derived from the provided {@code query}. Execution may be asynchronous, depending on the
     * QueryBus implementation.
     *
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param timeout      A timeout of {@code long} for the query
     * @param timeUnit     The selected {@link TimeUnit} for the given {@code timeout}
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A flux of results
     */
    default <R, Q> Flux<R> scatterGather(Q query, ResponseType<R> responseType, long timeout, TimeUnit timeUnit) {
        return scatterGather(query.getClass().getName(), query, responseType, timeout, timeUnit);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus}, expecting a response in the form of {@code responseType}
     * from several sources. The flux is completed when a {@code timeout} occurs or when all results are received.
     * Execution may be asynchronous, depending on the QueryBus implementation.
     *
     * @param queryName    A {@link String} describing the query to be executed
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param timeout      A timeout of {@code long} for the query
     * @param timeUnit     The selected {@link TimeUnit} for the given {@code timeout}
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A flux of results.
     */
    <R, Q> Flux<R> scatterGather(String queryName, Q query, ResponseType<R> responseType, long timeout,
                                 TimeUnit timeUnit);

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns a flux with initial results and with each update
     * emitted afterwards. The query name will be derived from the provided {@code query}.
     *
     * @param query        The {@code query} to be sent
     * @param responseType Will be used to match query handlers. As initial response type, it is consider to be {@link
     *                     org.axonframework.queryhandling.responsetypes.MultipleInstancesResponseType} of response
     *                     type, while incremental updates are consider to be of this type
     * @param <Q>          The query class
     * @param <R>          The type of {@link org.axonframework.queryhandling.responsetypes.MultipleInstancesResponseType}
     *                     for initial response type and type of incremental update
     * @return A flux of results.
     */
    default <Q, R> Flux<R> subscriptionQuery(Q query, Class<R> responseType) {
        return subscriptionQuery(query.getClass().getName(), query, responseType);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns a flux with initial results and with each update
     * emitted afterwards.
     *
     * @param queryName    A {@link String} describing the query to be executed
     * @param query        The {@code query} to be sent
     * @param responseType Will be used to match query handlers. As initial response type, it is consider to be {@link
     *                     org.axonframework.queryhandling.responsetypes.MultipleInstancesResponseType} of response
     *                     type, while incremental updates are consider to be of this type
     * @param <Q>          The query class
     * @param <R>          The type of {@link org.axonframework.queryhandling.responsetypes.MultipleInstancesResponseType}
     *                     for initial response type and type of incremental update
     * @return A flux of results
     */
    default <Q, R> Flux<R> subscriptionQuery(String queryName, Q query, Class<R> responseType) {
        return subscriptionQuery(queryName,
                                 query,
                                 responseType,
                                 FluxSink.OverflowStrategy.BUFFER,
                                 NoopBackpressure::new);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns a flux with initial results and with each update
     * emitted afterwards.
     *
     * @param queryName           A {@link String} describing the query to be executed
     * @param query               The {@code query} to be sent
     * @param responseType        Will be used to match query handlers. As initial response type, it is consider to be
     *                            {@link org.axonframework.queryhandling.responsetypes.MultipleInstancesResponseType} of
     *                            response type, while incremental updates are consider to be of this type
     * @param overflowStrategy    The overflow strategy describing backpressure for returned {@link Flux}
     * @param backpressureBuilder The backpressure builder describing backpressure mechanism for update handler of
     *                            subscription query
     * @param <Q>                 The query class
     * @param <R>                 The type of {@link org.axonframework.queryhandling.responsetypes.MultipleInstancesResponseType}
     *                            for initial response type and type of incremental update
     * @return A flux of results
     */
    <Q, R> Flux<R> subscriptionQuery(String queryName, Q query, Class<R> responseType,
                                     FluxSink.OverflowStrategy overflowStrategy,
                                     Function<UpdateHandler<List<R>, R>, BackpressuredUpdateHandler<List<R>, R>> backpressureBuilder);
}
