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

import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The mechanism that dispatches Query objects to their appropriate QueryHandlers. QueryHandlers can subscribe and
 * un-subscribe to specific queries (identified by their {@link QueryMessage#getQueryName()} and
 * {@link QueryMessage#getResponseType()} on the query bus. There may be multiple handlers for each combination of
 * queryName/responseName.
 *
 * @author Marc Gathier
 * @since 3.1
 */
public interface QueryBus {
    /**
     * Subscribe the given {@code handler} to queries with the given {@code queryName} and {@code responseName}.
     * Multiple handlers may subscribe to the same combination of queryName/responseName.
     *
     * @param queryName    the name of the query request to subscribe
     * @param responseType the type of response the subscribed component answers with
     * @param handler      a handler that implements the query
     * @return a handle to un-subscribe the query handler
     */
    <R> Registration subscribe(String queryName, Class<R> responseType, MessageHandler<? super QueryMessage<?, R>> handler);

    /**
     * Dispatch the given {@code query} to all QueryHandlers subscribed to the given {@code query}'s queryName/responseName.
     * Completes on the first result. It is up to the QueryBus implementor to decide what the definition of first result is.
     * <p>
     * When no handlers are available that can answer the query, the returned CompletableFuture will be completed with
     * a {@link NoHandlerForQueryException}.
     *
     * @param query the query
     * @param <Q>   the payload type of the query
     * @param <R>   the response type of the query
     * @return completable future for the first result
     */
    <Q, R> CompletableFuture<R> query(QueryMessage<Q, R> query);

    /**
     * Dispatch the given {@code query} to all QueryHandlers subscribed to the given {@code query}'s queryName/responseName.
     * Returns a stream of results which completes if all handlers have processed the request or when the timeout occurs.
     * <p>
     * If no handlers are available to provide a result, or when all available handlers throw an exception while
     * attempting to do so, the returned Stream is empty.
     *
     * @param query   the query
     * @param timeout time to wait for results
     * @param unit    unit for the timeout
     * @param <Q>     the payload type of the query
     * @param <R>     the response type of the query
     * @return stream of query results
     */
    <Q, R> Stream<R> queryAll(QueryMessage<Q, R> query, long timeout, TimeUnit unit);


}
