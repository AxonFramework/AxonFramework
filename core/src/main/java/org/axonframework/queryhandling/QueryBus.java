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

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The mechanism that dispatches Query objects to their appropriate QueryHandlers. QueryHandlers can subscribe and
 * un-subscribe to specific queries (identified by their {@link QueryMessage#getQueryName()} and
 * {@link QueryMessage#getResponseType()} on the query bus. There may be multiple handlers for each combination of
 * queryName/responseName.
 *
 * @author Marc Gathier
 * @author Allard Buijze
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
    <R> Registration subscribe(String queryName, Type responseType, MessageHandler<? super QueryMessage<?, R>> handler);

    /**
     * Dispatch the given {@code query} to a single QueryHandler subscribed to the given {@code query}'s queryName
     * and responseType. This method returns all values returned by the Query Handler as a Collection. This may or may
     * not be the exact collection as defined in the Query Handler.
     * <p>
     * If the Query Handler defines a single return object (i.e. not a collection or array), that object is returned
     * as the sole entry in a singleton collection.
     * <p>
     * When no handlers are available that can answer the given {@code query}, the returned CompletableFuture will be
     * completed with a {@link NoHandlerForQueryException}.
     *
     * @param query the query
     * @param <Q>   the payload type of the query
     * @param <R>   the response type of the query
     * @return a CompletableFuture that resolves when the response is available
     */
    <Q, R> CompletableFuture<QueryResponseMessage<R>> query(QueryMessage<Q, R> query);

    /**
     * Dispatch the given {@code query} to all QueryHandlers subscribed to the given {@code query}'s queryName/responseName.
     * Returns a stream of results which blocks until all handlers have processed the request or when the timeout occurs.
     * <p>
     * If no handlers are available to provide a result, or when all available handlers throw an exception while
     * attempting to do so, the returned Stream is empty.
     * <p>
     * Note that any terminal operation (such as {@link Stream#forEach(Consumer)}) on the Stream may cause it to
     * block until the {@code timeout} has expired, awaiting additional data to include in the stream.
     *
     * @param query   the query
     * @param timeout time to wait for results
     * @param unit    unit for the timeout
     * @param <Q>     the payload type of the query
     * @param <R>     the response type of the query
     * @return stream of query results
     */
    <Q, R> Stream<QueryResponseMessage<R>> scatterGather(QueryMessage<Q, R> query, long timeout, TimeUnit unit);
}
