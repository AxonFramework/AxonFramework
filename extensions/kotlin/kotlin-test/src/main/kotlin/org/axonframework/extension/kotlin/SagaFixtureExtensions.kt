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

import org.axonframework.test.saga.SagaTestFixture

/**
 * Creates a saga test fixture for saga [T].
 * @param [T] reified type of the saga.
 * @return saga test fixture.
 */
inline fun <reified T : Any> SagaTestFixture<T>.sagaTestFixture() =
        SagaTestFixture(T::class.java)

/**
 * Reified version of command gateway registration.
 * @param [T] saga type
 * @param [I] command gateway type.
 * @return registered command gateway instance.
 */
inline fun <T : Any, reified I : Any> SagaTestFixture<T>.registerCommandGateway(): I =
        this.registerCommandGateway(I::class.java)

/**
 * Reified version of command gateway registration.
 * @param [T] saga type
 * @param [I] command gateway type.
 * @param stubImplementation stub implementation.
 * @return registered command gateway instance.
 */
inline fun <T : Any, reified I : Any> SagaTestFixture<T>.registerCommandGateway(stubImplementation: I): I =
        this.registerCommandGateway(I::class.java, stubImplementation)

