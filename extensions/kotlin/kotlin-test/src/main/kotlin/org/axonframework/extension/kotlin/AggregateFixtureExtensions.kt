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

package org.axonframework.extension.kotlin

import org.axonframework.test.aggregate.AggregateTestFixture
import org.axonframework.test.aggregate.ResultValidator
import kotlin.reflect.KClass

/**
 * Creates an aggregate test fixture for aggregate [T].
 * @param T reified type of the aggregate.
 * @return aggregate test fixture.
 * @since 0.2.0
 */
inline fun <reified T : Any> AggregateTestFixture<T>.aggregateTestFixture() =
        AggregateTestFixture(T::class.java)

/**
 * Alias for the `when` method to avoid name clash with Kotlin's `when`.
 * @param command command to pass to the when method.
 * @param T aggregate type.
 * @return result validator.
 * @since 0.2.0
 */
fun <T : Any> AggregateTestFixture<T>.whenever(command: Any): ResultValidator<T> = this.`when`(command)

/**
 * Alias for the `when` method to avoid name clash with Kotlin's `when`.
 * @param command command to pass to the when method.
 * @param metaData metadata map.
 * @param T aggregate type.
 * @return result validator.
 * @since 0.2.0
 */
fun <T : Any> AggregateTestFixture<T>.whenever(command: Any, metaData: Map<String, Any>): ResultValidator<T> = this.`when`(command, metaData)

/**
 * Registers subtypes of this aggregate to support aggregate polymorphism. Command Handlers defined on this subtype
 * will be considered part of this aggregate's handlers.
 *
 * @param subtypes subtypes in this polymorphic hierarchy
 * @return the current FixtureConfiguration, for fluent interfacing
 * @since 0.2.0
 */
fun <T : Any> AggregateTestFixture<T>.withSubtypes(vararg subtypes: KClass<out T>) =
        this.withSubtypes(* subtypes.map { it.java }.toTypedArray())
