/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.extensions.kotlin

import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.QueryGateway
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Reified version of [QueryGateway.query]
 * which expects a collection as a response using [org.axonframework.messaging.responsetypes.MultipleInstancesResponseType]
 * @param query Query to send
 * @param [Q] the type of payload of the query
 * @param [R] the type of result of the query
 * @return [CompletableFuture] wrapping the result of the query
 * @see QueryGateway.query
 * @see ResponseTypes
 */
inline fun <reified R, reified Q> QueryGateway.queryForMultiple(query: Q): CompletableFuture<List<R>> {
    return this.query(query, ResponseTypes.multipleInstancesOf(R::class.java))
}

/**
 * Reified version of [QueryGateway.query] with explicit query name
 * which expects a collection as a response using [org.axonframework.messaging.responsetypes.MultipleInstancesResponseType]
 * @param queryName Name of the query
 * @param query Query to send
 * @param [Q] the type of payload of the query
 * @param [R] the type of result of the query
 * @return [CompletableFuture] wrapping the result of the query
 * @see QueryGateway.query
 * @see ResponseTypes
 */
inline fun <reified R, reified Q> QueryGateway.queryForMultiple(queryName: String, query: Q): CompletableFuture<List<R>> {
    return this.query(queryName, query, ResponseTypes.multipleInstancesOf(R::class.java))
}

/**
 * Reified version of [QueryGateway.query]
 * which expects a single object as a response using [org.axonframework.messaging.responsetypes.InstanceResponseType]
 * @param query Query to send
 * @param [Q] the type of payload of the query
 * @param [R] the type of result of the query
 * @return [CompletableFuture] wrapping the result of the query
 * @see QueryGateway.query
 * @see ResponseTypes
 */
inline fun <reified R, reified Q> QueryGateway.queryForSingle(query: Q): CompletableFuture<R> {
    return this.query(query, ResponseTypes.instanceOf(R::class.java))
}

/**
 * Reified version of [QueryGateway.query] with explicit query name
 * which expects a single object as a response using [org.axonframework.messaging.responsetypes.InstanceResponseType]
 * @param queryName Name of the query
 * @param query Query to send
 * @param [Q] the type of payload of the query
 * @param [R] the type of result of the query
 * @return [CompletableFuture] wrapping the result of the query
 * @see QueryGateway.query
 * @see ResponseTypes
 */
inline fun <reified R, reified Q> QueryGateway.queryForSingle(queryName: String, query: Q): CompletableFuture<R> {
    return this.query(queryName, query, ResponseTypes.instanceOf(R::class.java))
}

/**
 * Reified version of [QueryGateway.query]
 * which expects an Optional object as a response using [org.axonframework.messaging.responsetypes.OptionalResponseType]
 * @param query Query to send
 * @param [Q] the type of payload of the query
 * @param [R] the type of result of the query
 * @return [CompletableFuture] wrapping the result of the query
 * @see QueryGateway.query
 * @see ResponseTypes
 */
inline fun <reified R, reified Q> QueryGateway.queryForOptional(query: Q): CompletableFuture<Optional<R>> {
    return this.query(query, ResponseTypes.optionalInstanceOf(R::class.java))
}

/**
 * Reified version of [QueryGateway.query] with explicit query name
 * which expects an Optional object as a response using [org.axonframework.messaging.responsetypes.OptionalResponseType]
 * @param queryName Name of the query
 * @param query Query to send
 * @param [Q] the type of payload of the query
 * @param [R] the type of result of the query
 * @return [CompletableFuture] wrapping the result of the query
 * @see QueryGateway.query
 * @see ResponseTypes
 */
inline fun <reified R, reified Q> QueryGateway.queryForOptional(queryName: String, query: Q): CompletableFuture<Optional<R>> {
    return this.query(queryName, query, ResponseTypes.optionalInstanceOf(R::class.java))
}
