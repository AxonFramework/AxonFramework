package org.axonframework.extensions.kotlin

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.axonframework.queryhandling.QueryUpdateEmitter
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Tests [org.axonframework.queryhandling.QueryUpdateEmitter] extensions.
 *
 * @author Stefan Andjelkovic
 */
internal class QueryUpdateEmitterExtensionsTest {
    private val subjectEmitter = mockk<QueryUpdateEmitter>()
    private val exampleQuery = ExampleQuery(2)
    private val exampleUpdatePayload: String = "Updated"

    @BeforeTest
    fun before() {
        every { subjectEmitter.emit(ExampleQuery::class.java, match { it.test(exampleQuery) }, exampleUpdatePayload) } returns Unit
    }

    @Test
    fun `Emit extension should invoke correct emit method with a class parameter`() {
        subjectEmitter.emit<ExampleQuery, String>(exampleUpdatePayload) { it.value == exampleQuery.value }
        verify { subjectEmitter.emit(ExampleQuery::class.java, match { it.test(exampleQuery) }, exampleUpdatePayload) }
    }
}
