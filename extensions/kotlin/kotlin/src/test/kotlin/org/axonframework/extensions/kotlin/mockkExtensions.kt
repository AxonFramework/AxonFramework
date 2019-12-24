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
fun <T> MockKMatcherScope.instanceResponseTypeMatcher() = match { type: AbstractResponseType<T> -> type is InstanceResponseType }
fun <T> MockKMatcherScope.optionalResponseTypeMatcher() = match { type: AbstractResponseType<Optional<T>> -> type is OptionalResponseType<*> }
fun <T> MockKMatcherScope.multipleInstancesResponseTypeMatcher() = match { type: AbstractResponseType<List<T>> -> type is MultipleInstancesResponseType<*> }
