/*-
 * #%L
 * Axon Framework Kotlin Extension
 * %%
 * Copyright (C) 2019 AxonIQ
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * #L%
 */
package org.axonframework.extensions.kotlin

import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.QueryGateway
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Extensiond to access query gateway.
 */
object QueryGatewayExtensions {

    inline fun <reified R, reified Q> QueryGateway.queryForMultiple(queryName: String, query: Q): CompletableFuture<List<R>> {
        return this.query(queryName, query, ResponseTypes.multipleInstancesOf(R::class.java))
    }

    inline fun <reified R, reified Q> QueryGateway.queryForSingle(queryName: String, query: Q): CompletableFuture<R> {
        return this.query(queryName, query, ResponseTypes.instanceOf(R::class.java))
    }

    inline fun <reified R, reified Q> QueryGateway.queryForOptional(queryName: String, query: Q): CompletableFuture<Optional<R>> {
        return this.query(queryName, query, ResponseTypes.optionalInstanceOf(R::class.java))
    }

}
