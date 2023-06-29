/*
 * Copyright (c) 2010-2023. Axon Framework
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

import kotlin.test.Test

internal class FixtureExtensionsTest {

    @Test
    fun `Aggregate test fixture extension should create an aggregate fixture`() {
        aggregateTestFixture<ExampleAggregate>()
    }

    @Test
    fun `Saga test fixture extension should create a saga fixture`() {
        sagaTestFixture<ExampleSaga>()
    }

    @Test
    fun `Whenever extension should apply to an aggregate fixture`() {
        val fixture = aggregateTestFixture<ExampleAggregate>()

        fixture
                // Call on an AggregateTestFixture instance
                .whenever(ExampleCommand("id"))
                .expectNoEvents()

        fixture
                // Call on an AggregateTestFixture instance
                .whenever(ExampleCommand("id"), mapOf())
                .expectNoEvents()
    }

    @Test
    fun `Whenever extension should apply to a result validator`() {
        val fixture = aggregateTestFixture<ExampleAggregate>()

        fixture
                .givenNoPriorActivity()
                // Call on a ResultValidator instance
                .whenever(ExampleCommand("id"))
                .expectNoEvents()

        fixture
                .givenNoPriorActivity()
                // Call on a ResultValidator instance
                .whenever(ExampleCommand("id"), mapOf())
                .expectNoEvents()
    }

    @Test
    fun `Expect exception extension should accept a kotlin class`() {
        val fixture = aggregateTestFixture<ExampleAggregate>()
        fixture
                .whenever(ExampleCommandWithException("id"))
                .expectException(Exception::class)
    }
}