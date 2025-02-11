/*
 * Copyright (c) 2010-2024. Axon Framework
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
package org.axonframework.extensions.kotlin.serializer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.axonframework.eventhandling.GapAwareTrackingToken
import org.axonframework.eventhandling.GlobalSequenceTrackingToken
import org.axonframework.eventhandling.MergedTrackingToken
import org.axonframework.eventhandling.MultiSourceTrackingToken
import org.axonframework.eventhandling.ReplayToken
import org.axonframework.eventhandling.TrackingToken
import org.axonframework.eventhandling.scheduling.ScheduleToken
import org.axonframework.eventhandling.scheduling.java.SimpleScheduleToken
import org.axonframework.eventhandling.scheduling.quartz.QuartzScheduleToken
import org.axonframework.eventhandling.tokenstore.ConfigToken
import org.axonframework.extensions.kotlin.messaging.responsetypes.ArrayResponseType
import org.axonframework.extensions.kotlin.serialization.AxonSerializersModule
import org.axonframework.extensions.kotlin.serialization.KotlinSerializer
import org.axonframework.messaging.responsetypes.InstanceResponseType
import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType
import org.axonframework.messaging.responsetypes.OptionalResponseType
import org.axonframework.messaging.responsetypes.ResponseType
import org.axonframework.serialization.Serializer
import org.axonframework.serialization.SimpleSerializedObject
import org.axonframework.serialization.SimpleSerializedType
import org.axonframework.serialization.json.JacksonSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class AxonSerializersTest {

    private val serializer = KotlinSerializer(Json { serializersModule = AxonSerializersModule })

    @Test
    fun configToken() {
        val token = ConfigToken(mapOf("important-property" to "important-value", "other-property" to "other-value"))
        val json = """{"config":{"important-property":"important-value","other-property":"other-value"}}"""
        assertEquals(json, serializer.serialize(token, String::class.java).data)
        assertEquals(token, serializer.deserializeTrackingToken(token.javaClass.name, json))
    }

    @Test
    fun gapAwareTrackingToken() {
        val token = GapAwareTrackingToken(10, setOf(7, 8, 9))
        val json = """{"index":10,"gaps":[7,8,9]}"""
        assertEquals(json, serializer.serialize(token, String::class.java).data)
        assertEquals(token, serializer.deserializeTrackingToken(token.javaClass.name, json))
    }

    @Test
    fun multiSourceTrackingToken() {
        val token = MultiSourceTrackingToken(mapOf("primary" to GlobalSequenceTrackingToken(5), "secondary" to GlobalSequenceTrackingToken(10)))
        val json = """{"trackingTokens":{"primary":{"type":"org.axonframework.eventhandling.GlobalSequenceTrackingToken","globalIndex":5},"secondary":{"type":"org.axonframework.eventhandling.GlobalSequenceTrackingToken","globalIndex":10}}}"""
        assertEquals(json, serializer.serialize(token, String::class.java).data)
        assertEquals(token, serializer.deserializeTrackingToken(token.javaClass.name, json))
    }

    @Test
    fun mergedTrackingToken() {
        val token = MergedTrackingToken(GlobalSequenceTrackingToken(5), GlobalSequenceTrackingToken(10))
        val json = """{"lowerSegmentToken":{"type":"org.axonframework.eventhandling.GlobalSequenceTrackingToken","globalIndex":5},"upperSegmentToken":{"type":"org.axonframework.eventhandling.GlobalSequenceTrackingToken","globalIndex":10}}"""
        assertEquals(json, serializer.serialize(token, String::class.java).data)
        assertEquals(token, serializer.deserializeTrackingToken(token.javaClass.name, json))
    }

    @Test
    fun `replay token with String context`() {
        val token = ReplayToken.createReplayToken(
            GlobalSequenceTrackingToken(15), GlobalSequenceTrackingToken(10), "someContext"
        )
        val json = """{"tokenAtReset":{"type":"org.axonframework.eventhandling.GlobalSequenceTrackingToken","globalIndex":15},"currentToken":{"type":"org.axonframework.eventhandling.GlobalSequenceTrackingToken","globalIndex":10},"context":"someContext"}""".trimIndent()
        assertEquals(json, serializer.serialize(token, String::class.java).data)
        assertEquals(token, serializer.deserializeTrackingToken(token.javaClass.name, json))
    }

    @Test
    fun `replay token with currentToken with null value and null context`() {
        val token = ReplayToken.createReplayToken(GlobalSequenceTrackingToken(5), null, null)
        val json = """{"tokenAtReset":{"type":"org.axonframework.eventhandling.GlobalSequenceTrackingToken","globalIndex":5},"currentToken":null,"context":null}"""
        assertEquals(json, serializer.serialize(token, String::class.java).data)
        assertEquals(token, serializer.deserializeTrackingToken(token.javaClass.name, json))
    }

    @Test
    fun `replay token deserialize without context field`() {
        val token = ReplayToken.createReplayToken(GlobalSequenceTrackingToken(5), null, null)
        val json = """{"tokenAtReset":{"type":"org.axonframework.eventhandling.GlobalSequenceTrackingToken","globalIndex":5},"currentToken":null}"""
        assertEquals(token, serializer.deserializeTrackingToken(token.javaClass.name, json))
    }

    @Test
    fun `replay token with complex object as String context`() {
        @Serializable
        data class ComplexContext(val value1: String, val value2: Int, val value3: Boolean)
        val complexContext = ComplexContext("value1", 2, false)

        val token = ReplayToken.createReplayToken(
            GlobalSequenceTrackingToken(15),
            GlobalSequenceTrackingToken(10),
            Json.encodeToString(complexContext)
        )
        val json = """{"tokenAtReset":{"type":"org.axonframework.eventhandling.GlobalSequenceTrackingToken","globalIndex":15},"currentToken":{"type":"org.axonframework.eventhandling.GlobalSequenceTrackingToken","globalIndex":10},"context":"{\"value1\":\"value1\",\"value2\":2,\"value3\":false}"}""".trimIndent()
        assertEquals(json, serializer.serialize(token, String::class.java).data)
        val deserializedToken = serializer.deserializeTrackingToken(token.javaClass.name, json) as ReplayToken
        assertEquals(token, deserializedToken)
        assertInstanceOf(String::class.java, deserializedToken.context())
        assertEquals(complexContext, Json.decodeFromString<ComplexContext>(deserializedToken.context() as String))
    }

    @Test
    fun globalSequenceTrackingToken() {
        val token = GlobalSequenceTrackingToken(5)
        val json = """{"globalIndex":5}"""
        assertEquals(json, serializer.serialize(token, String::class.java).data)
        assertEquals(token, serializer.deserializeTrackingToken(token.javaClass.name, json))
    }

    @Test
    fun simpleScheduleToken() {
        val token = SimpleScheduleToken("my-token-id")
        val json = """{"tokenId":"my-token-id"}"""
        assertEquals(json, serializer.serialize(token, String::class.java).data)
        assertEquals(token, serializer.deserializeScheduleToken(token.javaClass.name, json))
    }

    @Test
    fun quartzScheduleToken() {
        val token = QuartzScheduleToken("my-job-id", "my-group-id")
        val json = """{"jobIdentifier":"my-job-id","groupIdentifier":"my-group-id"}"""
        assertEquals(json, serializer.serialize(token, String::class.java).data)
        assertEquals(token, serializer.deserializeScheduleToken(token.javaClass.name, json))
    }

    @Test
    fun instanceResponseType() {
        val responseType = InstanceResponseType(String::class.java)
        val json = """{"expectedResponseType":"java.lang.String"}"""
        assertEquals(json, serializer.serialize(responseType, String::class.java).data)
        assertEquals(responseType, serializer.deserializeResponseType(responseType.javaClass.name, json))
    }

    @Test
    fun optionalResponseType() {
        val responseType = OptionalResponseType(String::class.java)
        val json = """{"expectedResponseType":"java.lang.String"}"""
        assertEquals(json, serializer.serialize(responseType, String::class.java).data)
        assertEquals(responseType, serializer.deserializeResponseType(responseType.javaClass.name, json))
    }

    @Test
    fun multipleInstancesResponseType() {
        val responseType = MultipleInstancesResponseType(String::class.java)
        val json = """{"expectedResponseType":"java.lang.String"}"""
        assertEquals(json, serializer.serialize(responseType, String::class.java).data)
        assertEquals(responseType, serializer.deserializeResponseType(responseType.javaClass.name, json))
    }

    @Test
    fun arrayResponseType() {
        val responseType = ArrayResponseType(String::class.java)
        val json = """{"expectedResponseType":"java.lang.String"}"""
        assertEquals(json, serializer.serialize(responseType, String::class.java).data)
        assertEquals(responseType, serializer.deserializeResponseType(responseType.javaClass.name, json))
    }

    private fun Serializer.deserializeTrackingToken(tokenType: String, json: String): TrackingToken =
        deserializeJson(tokenType, json)

    private fun Serializer.deserializeScheduleToken(tokenType: String, json: String): ScheduleToken =
        deserializeJson(tokenType, json)

    private fun Serializer.deserializeResponseType(responseType: String, json: String): ResponseType<*> =
        deserializeJson(responseType, json)

    private fun <T> Serializer.deserializeJson(type: String, json: String): T {
        val serializedType = SimpleSerializedType(type, null)
        val serializedToken = SimpleSerializedObject(json, String::class.java, serializedType)
        return deserialize(serializedToken)
    }

}
