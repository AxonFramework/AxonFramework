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

package org.axonframework.extension.kotlin.test

import org.axonframework.test.aggregate.AggregateTestFixture
import org.axonframework.test.aggregate.FixtureConfiguration
import org.axonframework.test.aggregate.ResultValidator
import org.axonframework.test.aggregate.TestExecutor
import org.axonframework.test.saga.SagaTestFixture
import kotlin.reflect.KClass

/**
 * Creates an aggregate test fixture for aggregate [T].
 * @param T reified type of the aggregate.
 * @return aggregate test fixture.
 * @since 0.2.0
 */
inline fun <reified T : Any> aggregateTestFixture() =
        AggregateTestFixture(T::class.java)

/**
 * Alias for the `when` method to avoid name clash with Kotlin's `when`.
 * @param command command to pass to the when method.
 * @param T aggregate type.
 * @return result validator.
 * @since 0.2.0
 */
fun <T : Any> TestExecutor<T>.whenever(command: Any): ResultValidator<T> = this.`when`(command)

/**
 * Alias for the `when` method to avoid name clash with Kotlin's `when`.
 * @param command command to pass to the when method.
 * @param metaData metadata map.
 * @param T aggregate type.
 * @return result validator.
 * @since 0.2.0
 */
fun <T : Any> TestExecutor<T>.whenever(command: Any, metaData: Map<String, Any>): ResultValidator<T> = this.`when`(command, metaData)

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

/**
 * Indicates that a field with given {@code fieldName}, which is declared in given {@code declaringClass}
 * is ignored when performing deep equality checks.
 *
 * @param T type of fixture target.
 * @param F filed type.
 * @param fieldName The name of the field
 * @return the current FixtureConfiguration, for fluent interfacing
 * @since 0.2.0
 */
inline fun <T : Any, reified F : Any> FixtureConfiguration<T>.registerIgnoredField(fieldName: String): FixtureConfiguration<T> =
        this.registerIgnoredField(F::class.java, fieldName)

/**
 * Creates a saga test fixture for saga [T].
 * @param T reified type of the saga.
 * @return saga test fixture.
 * @since 0.2.0
 */
inline fun <reified T : Any> sagaTestFixture() =
        SagaTestFixture(T::class.java)

/**
 * Reified version of command gateway registration.
 * @param T saga type
 * @param I command gateway type.
 * @return registered command gateway instance.
 * @since 0.2.0
 */
inline fun <T : Any, reified I : Any> SagaTestFixture<T>.registerCommandGateway(): I =
        this.registerCommandGateway(I::class.java)

/**
 * Reified version of command gateway registration.
 * @param T saga type
 * @param I command gateway type.
 * @param stubImplementation stub implementation.
 * @return registered command gateway instance.
 * @since 0.2.0
 */
inline fun <T : Any, reified I : Any> SagaTestFixture<T>.registerCommandGateway(stubImplementation: I): I =
        this.registerCommandGateway(I::class.java, stubImplementation)

/**
 * Expect a KClass exception for aggregate [T].
 * @param T aggregate type
 * @param E exception type
 * @param expectedException kotlin class of the exception
 * @return this
 * @since 0.2.0
 */
fun <T : Any, E : KClass<out Throwable>> ResultValidator<T>.expectException(expectedException: E): ResultValidator<T> =
        this.expectException(expectedException.java)