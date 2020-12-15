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

        fixture
                // Call on an AggregateTestFixture instance
                .whenever(ExampleCommand("id"), mapOf())
    }

    @Test
    fun `Whenever extension should apply to a result validator`() {
        val fixture = aggregateTestFixture<ExampleAggregate>()

        fixture
                .given()
                // Call on a ResultValidator instance
                .whenever(ExampleCommand("id"))

        fixture
                .given()
                // Call on a ResultValidator instance
                .whenever(ExampleCommand("id"), mapOf())
    }

    @Test
    fun `Expect exception extension should accept a kotlin class`() {
        val fixture = aggregateTestFixture<ExampleAggregate>()
        fixture
                .whenever(ExampleCommand("id"))
                .expectException(Exception::class)
    }
}