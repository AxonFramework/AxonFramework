/*
 * Copyright (c) 2010-2021. Axon Framework
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

import io.mockk.MockKMatcherScope
import io.mockk.MockKVerificationScope
import org.axonframework.messaging.responsetypes.AbstractResponseType
import org.axonframework.messaging.responsetypes.InstanceResponseType
import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType
import org.axonframework.messaging.responsetypes.OptionalResponseType
import org.axonframework.messaging.responsetypes.ResponseType
import java.util.*

/**
 * Matches wrapped type of given [ResponseType] instead of entire [ResponseType] object
 * @param clazz Class object of the type to match
 * @param T Type wrapped by [ResponseType]
 */
internal fun <T> MockKVerificationScope.matchExpectedResponseType(clazz: Class<T>) = match { type: ResponseType<T> -> type.expectedResponseType == clazz }

/**
 * Matches that given [ResponseType] is [InstanceResponseType]
 * @param T Type wrapped by given [ResponseType] instance. Required for Mockk but will not be explicitly matched
 */
internal fun <T> MockKMatcherScope.matchInstanceResponseType() = match { type: AbstractResponseType<T> -> type is InstanceResponseType }

/**
 * Matches that given [ResponseType] is [OptionalResponseType]. Will not check type wrapped by the [Optional] instance
 * @param T Type wrapped by given [ResponseType] instance. Required for Mockk but will not be explicitly matched
 */
internal fun <T> MockKMatcherScope.matchOptionalResponseType() = match { type: AbstractResponseType<Optional<T>> -> type is OptionalResponseType }

/**
 * Matches that given [ResponseType] is [MultipleInstancesResponseType]. Will not check type wrapped by the [List] instance
 * @param T Type wrapped by given [ResponseType] instance. Required for Mockk but will not be explicitly matched
 */
internal fun <T> MockKMatcherScope.matchMultipleInstancesResponseType() = match { type: AbstractResponseType<List<T>> -> type is MultipleInstancesResponseType<*> }
