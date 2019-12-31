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

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.axonframework.messaging.responsetypes.AbstractResponseType
import org.axonframework.messaging.responsetypes.InstanceResponseType
import org.axonframework.queryhandling.QueryGateway
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.test.assertSame
import kotlin.test.assertTrue

class QueryGatewayExtensionsTest {

    private val queryName = ExampleQuery::class.qualifiedName.toString()
    private val exampleQuery = ExampleQuery(2)
    private val instanceReturnValue: CompletableFuture<String> = CompletableFuture.completedFuture("2")
    private val optionalReturnValue: CompletableFuture<Optional<String>> = CompletableFuture.completedFuture(Optional.of("Value"))
    private val listReturnValue: CompletableFuture<List<String>> = CompletableFuture.completedFuture(listOf("Value", "Second value"))
    private val subjectGateway = mockk<QueryGateway>()

    @Before
    fun before() {
        every { subjectGateway.query(queryName, exampleQuery, instanceResponseTypeMatcher<String>()) } returns instanceReturnValue
        every { subjectGateway.query(queryName, exampleQuery, optionalResponseTypeMatcher<String>()) } returns optionalReturnValue
        every { subjectGateway.query(queryName, exampleQuery, multipleInstancesResponseTypeMatcher<String>()) } returns listReturnValue
    }

    @After
    fun after() {
        clearMocks(subjectGateway)
    }

    @Test
    fun `Query for Single should invoke query method with correct generic parameters`() {
        val queryResult = subjectGateway.queryForSingle<String, ExampleQuery>(queryName = queryName, query = exampleQuery)

        assertSame(queryResult, instanceReturnValue)
        verify(exactly = 1) { subjectGateway.query(queryName, exampleQuery, responseTypeOfMatcher(String::class.java)) }
    }

    @Test
    fun `Query for Single should invoke query method and not require explicit generic types`() {
        val queryResult: CompletableFuture<String> = subjectGateway.queryForSingle(queryName = queryName, query = exampleQuery)

        assertSame(queryResult, instanceReturnValue)
        verify(exactly = 1) { subjectGateway.query(queryName, exampleQuery, responseTypeOfMatcher(String::class.java)) }
    }

    @Test
    fun `Query for Optional should invoke query method with correct generic parameters`() {
        val queryResult = subjectGateway.queryForOptional<String, ExampleQuery>(queryName = queryName, query = exampleQuery)

        assertSame(queryResult, optionalReturnValue)
        verify(exactly = 1) { subjectGateway.query(queryName, exampleQuery, responseTypeOfMatcher(String::class.java)) }
    }

    @Test
    fun `Query for Optional should invoke query method and not require explicit generic types`() {
        val queryResult: CompletableFuture<Optional<String>> = subjectGateway.queryForOptional(queryName = queryName, query = exampleQuery)

        assertSame(queryResult, optionalReturnValue)
        verify(exactly = 1) { subjectGateway.query(queryName, exampleQuery, responseTypeOfMatcher(String::class.java)) }
    }

    @Test
    fun `Query for Multiple should invoke query method with correct generic parameters`() {
        val queryResult = subjectGateway.queryForMultiple<String, ExampleQuery>(queryName = queryName, query = exampleQuery)

        assertSame(queryResult, listReturnValue)
        verify(exactly = 1) { subjectGateway.query(queryName, exampleQuery, responseTypeOfMatcher(String::class.java)) }
    }

    @Test
    fun `Query for Multiple should invoke query method and not require explicit generic types`() {
        val queryResult: CompletableFuture<List<String>> = subjectGateway.queryForMultiple(queryName = queryName, query = exampleQuery)

        assertSame(queryResult, listReturnValue)
        verify(exactly = 1) { subjectGateway.query(queryName, exampleQuery, responseTypeOfMatcher(String::class.java)) }
    }

    @Test
    fun `Query for Single should handle nullable responses`() {
        val nullInstanceReturnValue: CompletableFuture<String?> = CompletableFuture.completedFuture(null)
        val nullableQueryGateway = mockk<QueryGateway> {
            every { query(queryName, exampleQuery, match { i: AbstractResponseType<String?> -> i is InstanceResponseType }) } returns nullInstanceReturnValue
        }

        val queryResult = nullableQueryGateway.queryForSingle<String?, ExampleQuery>(queryName = queryName, query = exampleQuery)

        assertSame(queryResult, nullInstanceReturnValue)
        assertTrue(nullInstanceReturnValue.get() == null)
        verify(exactly = 1) { nullableQueryGateway.query(queryName, exampleQuery, responseTypeOfMatcher(String::class.java)) }
    }

}

private class ExampleQuery(val value: Number)
