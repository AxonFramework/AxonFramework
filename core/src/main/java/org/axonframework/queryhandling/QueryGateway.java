/*
 * Copyright (c) 2010-2017. Axon Framework
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

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Interface towards the Query Handling components of an application. This interface provides a friendlier API toward
 * the query bus.
 *
 * @author Marc Gathier
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.1
 */
public interface QueryGateway {

    /**
     * Sends given {@code query} over the query bus, expecting a response with the given {@code responseType}.
     * The query name will be derived from the provided {@code query}.
     * The query result will come from a single source and execution may be asynchronous.
     *
     * @param query        The {@code query} to be sent
     * @param responseType A {@link java.lang.Class} describing the desired response type
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link java.util.concurrent.CompletableFuture} containing the query result as dictated by the given
     * {@code responseType}
     */
    default <R, Q> CompletableFuture<R> query(Q query, Class<R> responseType) {
        return query(query.getClass().getName(), query, responseType);
    }

    /**
     * Sends given {@code query} over the query bus, expecting a response with the given {@code responseType}.
     * The query result will come from a single source and execution may be asynchronous.
     *
     * @param queryName    A {@link java.lang.String} describing the query to be executed
     * @param query        The {@code query} to be sent
     * @param responseType The {@link org.axonframework.queryhandling.ResponseType} used for this query
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link java.util.concurrent.CompletableFuture} containing the query result as dictated by the given
     * {@code responseType}
     */
    default <R, Q> CompletableFuture<R> query(String queryName, Q query, Class<R> responseType) {
        return query(queryName, query, ResponseTypes.instanceOf(responseType));
    }

    /**
     * Sends given {@code query} over the query bus, expecting a response in the form of {@code responseType}.
     * The query name will be derived from the provided {@code query}.
     * The query result will come from a single source and execution may be asynchronous.
     *
     * @param query        The {@code query} to be sent
     * @param responseType The {@link org.axonframework.queryhandling.ResponseType} used for this query
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link java.util.concurrent.CompletableFuture} containing the query result as dictated by the given
     * {@code responseType}
     */
    default <R, Q> CompletableFuture<R> query(Q query, ResponseType<R> responseType) {
        return query(query.getClass().getName(), query, responseType);
    }

    /**
     * Sends given {@code query} over the query bus, expecting a response in the form of {@code responseType}.
     * The query result will come from a single source and execution may be asynchronous.
     *
     * @param queryName    A {@link java.lang.String} describing the query to be executed
     * @param query        The {@code query} to be sent
     * @param responseType The {@link org.axonframework.queryhandling.ResponseType} used for this query
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link java.util.concurrent.CompletableFuture} containing the query result as dictated by the given
     * {@code responseType}
     */
    <R, Q> CompletableFuture<R> query(String queryName, Q query, ResponseType<R> responseType);

    /**
     * Sends given query to the query bus and expects a stream of results with name resultName. The stream is completed
     * when a timeout occurs or when all results are received.
     *
     * @param <R>         The type of result expected from query execution.
     * @param <Q>         The query class.
     * @param queryName   The name of the query.
     * @param query       The query.
     * @param resultClass Type type of result.
     * @param timeout     Timeout for the request.
     * @param timeUnit    Unit for the timeout.
     * @return A stream of results.
     */
    <R, Q> Stream<Collection<R>> scatterGather(String queryName, Q query, Class<R> resultClass, long timeout,
                                               TimeUnit timeUnit);

    /**
     * Sends given query to the query bus and expects a stream of results with type responseType. The stream is
     * completed when a timeout occurs or when all results are received.
     *
     * @param query        The query.
     * @param responseType The expected result type.
     * @param timeout      Timeout for the request.
     * @param timeUnit     Unit for the timeout.
     * @param <R>          The type of result expected from query execution.
     * @param <Q>          The query class.
     * @return A stream of results.
     *
     * @throws NullPointerException when query is null.
     */
    default <R, Q> Stream<Collection<R>> scatterGather(Q query, Class<R> responseType, long timeout,
                                                       TimeUnit timeUnit) {
        return scatterGather(query.getClass().getName(), query, responseType, timeout, timeUnit);
    }

    /**
     * Sends given query to the query bus and expects a result of type resultClass. Execution may be asynchronous.
     *
     * @param query        The query.
     * @param queryName    The name of the query.
     * @param responseType The expected result type.
     * @param <R>          The type of result expected from query execution.
     * @param <Q>          The query class.
     * @return A completable future that contains the first result of the query..
     *
     * @throws NullPointerException when query is null.
     * @deprecated Use {@link #query(String, Object, Class)} instead.
     */
    @Deprecated
    default <R, Q> CompletableFuture<R> send(String queryName, Q query, Class<R> responseType) {
        return query(queryName, query, responseType);
    }

    /**
     * Sends given query to the query bus and expects a result of type resultClass. Execution may be asynchronous.
     *
     * @param query        The query.
     * @param responseType The expected result type.
     * @param <R>          The type of result expected from query execution.
     * @param <Q>          The query class.
     * @return A completable future that contains the first result of the query.
     *
     * @throws NullPointerException when query is null.
     * @deprecated Use {@link #query(Object, Class)} instead.
     */
    @Deprecated
    default <R, Q> CompletableFuture<R> send(Q query, Class<R> responseType) {
        return query(query.getClass().getName(), query, responseType);
    }

    /**
     * Sends given query to the query bus and expects a stream of results with type responseType. The stream is
     * completed when a timeout occurs or when all results are received.
     *
     * @param query        The query.
     * @param responseType The expected result type.
     * @param timeout      Timeout for the request.
     * @param timeUnit     Unit for the timeout.
     * @param <R>          The type of result expected from query execution.
     * @param <Q>          The query class.
     * @return A stream of results.
     *
     * @throws NullPointerException when query is null.
     * @deprecated Use {@link #scatterGather(Object, Class, long, TimeUnit)} instead.
     */
    @Deprecated
    default <R, Q> Stream<R> send(Q query, Class<R> responseType, long timeout, TimeUnit timeUnit) {
        return scatterGather(query.getClass().getName(), query, responseType, timeout, timeUnit)
                .flatMap(Collection::stream);
    }

    /**
     * Sends given query to the query bus and expects a stream of results with name resultName. The stream is completed
     * when a timeout occurs or when all results are received.
     *
     * @param query       The query.
     * @param queryName   The name of the query.
     * @param resultClass Type type of result.
     * @param timeout     Timeout for the request.
     * @param timeUnit    Unit for the timeout.
     * @param <R>         The type of result expected from query execution.
     * @param <Q>         The query class.
     * @return A stream of results.
     *
     * @deprecated Use {@link #scatterGather(String, Object, Class, long, TimeUnit)} instead.
     */
    @Deprecated
    default <R, Q> Stream<R> send(Q query, String queryName, Class<R> resultClass, long timeout, TimeUnit timeUnit) {
        return scatterGather(queryName, query, resultClass, timeout, timeUnit).flatMap(Collection::stream);
    }
}
