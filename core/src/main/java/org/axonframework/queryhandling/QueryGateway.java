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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Interface towards the Query Handling components of an application. This interface provides a friendlier API toward
 * the query bus.
 *
 * @since 3.1
 * @author Marc Gathier
 */
public interface QueryGateway {
    /**
     * sends given query to the query bus and expects a result with name resultName. Execution may be asynchronous.
     * @param query the query
     * @param resultName the expected result name
     * @param <R> The type of result expected from query execution
     * @param <Q> The query class
     * @return a completable future that contains the first result of the query.
     */
    <R,Q> CompletableFuture<R> send(Q query, String resultName);

    /**
     * sends given query to the query bus and expects a result of type resultClass. Execution may be asynchronous.
     * @param query the query
     * @param resultClass the expected result type
     * @param <R> The type of result expected from query execution
     * @param <Q> The query class
     * @return a completable future that contains the first result of the query.
     */
    <R,Q> CompletableFuture<R> send(Q query, Class<R> resultClass);

    /**
     * sends given query to the query bus and expects a result with name resultName. Execution may be asynchronous.
     * @param query the query
     * @param queryName the name of the query
     * @param resultName the expected result name
     * @param <R> The type of result expected from query execution
     * @param <Q> The query class
     * @return a completable future that contains the first result of the query.
     */
    <R,Q> CompletableFuture<R> send(Q query, String queryName, String resultName);


    /**
     * sends given query to the query bus and expects a stream of results with name resultName. The stream is completed when a timeout occurs
     * or when all results are received.
     * @param query the query
     * @param resultName the expected result name
     * @param timeout timeout for the request
     * @param timeUnit unit for the timeout
     * @param <R> The type of result expected from query execution
     * @param <Q> The query class
     * @return a stream of results
     */
    <R,Q> Stream<R> send(Q query, String resultName, long timeout, TimeUnit timeUnit);
    /**
     * sends given query to the query bus and expects a stream of results with name resultName. The stream is completed when a timeout occurs
     * or when all results are received.
     * @param query the query
     * @param queryName the name of the query
     * @param resultName the expected result name
     * @param timeout timeout for the request
     * @param timeUnit unit for the timeout
     * @param <R> The type of result expected from query execution
     * @param <Q> The query class
     * @return a stream of results
     */
    <R,Q> Stream<R> send(Q query, String queryName, String resultName, long timeout, TimeUnit timeUnit);

    /**
     * sends given query to the query bus and expects a stream of results with type resultClass. The stream is completed when a timeout occurs
     * or when all results are received.
     * @param query the query
     * @param resultClass the expected result type
     * @param timeout timeout for the request
     * @param timeUnit unit for the timeout
     * @param <R> The type of result expected from query execution
     * @param <Q> The query class
     * @return a stream of results
     */
    <R,Q> Stream<R> send(Q query, Class<R> resultClass, long timeout, TimeUnit timeUnit);
}
