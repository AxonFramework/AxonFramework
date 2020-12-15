package org.axonframework.extension.kotlin.test

import org.axonframework.test.aggregate.AggregateTestFixture
import kotlin.test.Test

internal class FixtureExtensionsTest {

    @Test
    fun `Expect exception extension should accept a kotlin class`() {
        val fixture = AggregateTestFixture(ExampleAggregate::class.java)
        fixture
                .`when`(ExampleCommand("id"))
                .expectException(Exception::class)
    }
}