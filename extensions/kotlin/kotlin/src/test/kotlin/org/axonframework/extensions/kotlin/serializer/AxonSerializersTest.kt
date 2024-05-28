package org.axonframework.extensions.kotlin.serializer

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
import org.axonframework.extensions.kotlin.serialization.ArrayResponseType
import org.axonframework.extensions.kotlin.serialization.AxonSerializersModule
import org.axonframework.extensions.kotlin.serialization.KotlinSerializer
import org.axonframework.messaging.responsetypes.InstanceResponseType
import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType
import org.axonframework.messaging.responsetypes.OptionalResponseType
import org.axonframework.messaging.responsetypes.ResponseType
import org.axonframework.serialization.Serializer
import org.axonframework.serialization.SimpleSerializedObject
import org.axonframework.serialization.SimpleSerializedType
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun replayToken() {
        val token = ReplayToken.createReplayToken(GlobalSequenceTrackingToken(15), GlobalSequenceTrackingToken(10))
        val json = """{"tokenAtReset":{"type":"org.axonframework.eventhandling.GlobalSequenceTrackingToken","globalIndex":15},"currentToken":{"type":"org.axonframework.eventhandling.GlobalSequenceTrackingToken","globalIndex":10}}"""
        assertEquals(json, serializer.serialize(token, String::class.java).data)
        assertEquals(token, serializer.deserializeTrackingToken(token.javaClass.name, json))
    }

    @Test
    fun `replay token with currentToken with null value`() {
        val token = ReplayToken.createReplayToken(GlobalSequenceTrackingToken(5), null)
        val json = """{"tokenAtReset":{"type":"org.axonframework.eventhandling.GlobalSequenceTrackingToken","globalIndex":5},"currentToken":null}"""
        assertEquals(json, serializer.serialize(token, String::class.java).data)
        assertEquals(token, serializer.deserializeTrackingToken(token.javaClass.name, json))
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
