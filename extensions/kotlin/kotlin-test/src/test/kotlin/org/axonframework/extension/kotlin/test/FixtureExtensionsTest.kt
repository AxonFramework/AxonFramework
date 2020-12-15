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
    fun `Expect exception extension should accept a kotlin class`() {
        val fixture = aggregateTestFixture<ExampleAggregate>()
        fixture
                .`when`(ExampleCommand("id"))
                .expectException(Exception::class)
    }
}