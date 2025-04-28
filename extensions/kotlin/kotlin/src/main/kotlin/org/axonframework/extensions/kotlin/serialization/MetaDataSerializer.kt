package org.axonframework.extensions.kotlin.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import org.axonframework.messaging.MetaData

object MetaDataSerializer : KSerializer<MetaData> {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private val mapSerializer = MapSerializer(
        keySerializer = String.serializer(),
        valueSerializer = JsonElement.serializer()
    )

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = mapSerialDescriptor(
        String.serializer().descriptor,
        JsonElement.serializer().descriptor
    )

    override fun serialize(encoder: Encoder, value: MetaData) {
        val jsonMap = value.entries.associate { (key, rawValue) ->
            key to toJsonElement(rawValue)
        }
        encoder.encodeSerializableValue(mapSerializer, jsonMap)
    }

    override fun deserialize(decoder: Decoder): MetaData {
        val map = decoder.decodeSerializableValue(mapSerializer)
        val restored = map.mapValues { (_, jsonElement) -> fromJsonElement(jsonElement) }
        return MetaData(restored)
    }

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map { toJsonElement(it) })
        is Map<*, *> -> JsonObject(
            value.entries.associate { (k, v) ->
                k.toString() to toJsonElement(v)
            }
        )
        else -> JsonPrimitive(value.toString()) // Fallback toString()
    }

    private fun fromJsonElement(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.boolean
            element.intOrNull != null -> element.int
            element.longOrNull != null -> element.long
            element.floatOrNull != null -> element.float
            element.doubleOrNull != null -> element.double
            else -> element.content
        }
        is JsonObject -> element.mapValues { fromJsonElement(it.value) }
        is JsonArray -> element.map { fromJsonElement(it) }
    }
}