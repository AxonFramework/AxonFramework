/*-
 * #%L
 * Axon Framework - Kotlin Extension
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

import io.mockk.MockKMatcherScope
import io.mockk.MockKVerificationScope
import org.axonframework.messaging.responsetypes.AbstractResponseType
import org.axonframework.messaging.responsetypes.InstanceResponseType
import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType
import org.axonframework.messaging.responsetypes.OptionalResponseType
import org.axonframework.messaging.responsetypes.ResponseType
import java.util.*

internal fun <T> MockKVerificationScope.responseTypeOfMatcher(clazz: Class<T>) = match { type: ResponseType<T> -> type.expectedResponseType == clazz }
internal fun <T> MockKMatcherScope.instanceResponseTypeMatcher() = match { type: AbstractResponseType<T> -> type is InstanceResponseType }
internal fun <T> MockKMatcherScope.optionalResponseTypeMatcher() = match { type: AbstractResponseType<Optional<T>> -> type is OptionalResponseType<*> }
internal fun <T> MockKMatcherScope.multipleInstancesResponseTypeMatcher() = match { type: AbstractResponseType<List<T>> -> type is MultipleInstancesResponseType<*> }
