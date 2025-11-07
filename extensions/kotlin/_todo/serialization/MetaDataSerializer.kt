package org.axonframework.extensions.kotlin.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import org.axonframework.messaging.MetaData
import java.time.Instant
import java.util.UUID

/**
 * A composite Kotlinx [KSerializer] for Axon Framework's [MetaData] type that selects the
 * appropriate serializer based on the encoder/decoder type.
 *
 * This serializer delegates to:
 * - [JsonMetaDataSerializer] when used with [JsonEncoder]/[JsonDecoder]
 * - [StringMetaDataSerializer] for all other encoder/decoder types
 *
 * This allows efficient JSON serialization without unnecessary string encoding, while
 * maintaining compatibility with all other serialization formats through string-based
 * serialization.
 *
 * @author Mateusz Nowak
 * @since 4.11.2
 */
object ComposedMetaDataSerializer : KSerializer<MetaData> {
    override val descriptor: SerialDescriptor = StringMetaDataSerializer.descriptor

    override fun serialize(encoder: Encoder, value: MetaData) {
        when (encoder) {
            is JsonEncoder -> JsonMetaDataSerializer.serialize(encoder, value)
            else -> StringMetaDataSerializer.serialize(encoder, value)
        }
    }

    override fun deserialize(decoder: Decoder): MetaData {
        return when (decoder) {
            is JsonDecoder -> JsonMetaDataSerializer.deserialize(decoder)
            else -> StringMetaDataSerializer.deserialize(decoder)
        }
    }
}

/**
 * A Kotlinx [KSerializer] for Axon Framework's [MetaData] type, suitable for serialization across any format.
 *
 * This serializer converts a [MetaData] instance to a JSON-encoded [String] using a recursive conversion
 * of all entries into [JsonElement]s. This JSON string is then serialized using [String.serializer()],
 * ensuring compatibility with any serialization format.
 *
 * ### Supported value types
 * Each entry in the MetaData map must conform to one of the following:
 * - Primitives: [String], [Int], [Long], [Float], [Double], [Boolean]
 * - Complex types: [UUID], [Instant]
 * - Collections: [Collection], [List], [Set]
 * - Arrays: [Array]
 * - Nested Maps: [Map] with keys convertible to [String]
 *
 * ### Limitations
 * - Custom types that do not fall into the above categories will throw a [SerializationException]
 * - Deserialized non-primitive types (like [UUID], [Instant]) are restored as [String], not their original types
 *
 * This serializer guarantees structural integrity of nested metadata while remaining format-agnostic.
 *
 * @author Mateusz Nowak
 * @since 4.11.1
 */
object StringMetaDataSerializer : KSerializer<MetaData> {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun serialize(encoder: Encoder, value: MetaData) {
        val map: Map<String, JsonElement> = value.entries.associate { (key, rawValue) ->
            key to toJsonElement(rawValue)
        }
        val jsonString = json.encodeToString(JsonObject(map))
        encoder.encodeString(jsonString)
    }

    override fun deserialize(decoder: Decoder): MetaData {
        val jsonString = decoder.decodeString()
        val jsonObject = json.parseToJsonElement(jsonString).jsonObject
        val reconstructed = jsonObject.mapValues { (_, jsonElement) ->
            fromJsonElement(jsonElement)
        }
        return MetaData(reconstructed)
    }

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is UUID -> JsonPrimitive(value.toString())
        is Instant -> JsonPrimitive(value.toString())
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) ->
            k.toString() to toJsonElement(v)
        })
        is Collection<*> -> JsonArray(value.map { toJsonElement(it) })
        is Array<*> -> JsonArray(value.map { toJsonElement(it) })
        else -> throw SerializationException("Unsupported type: ${value::class}")
    }

    private fun fromJsonElement(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> {
            if (element.isString) {
                element.content
            } else {
                element.booleanOrNull ?: element.intOrNull ?: element.longOrNull ?:
                element.floatOrNull ?: element.doubleOrNull ?: element.content
            }
        }
        is JsonObject -> element.mapValues { fromJsonElement(it.value) }
        is JsonArray -> element.map { fromJsonElement(it) }
    }
}

/**
 * A Kotlinx [KSerializer] for Axon Framework's [MetaData] type, optimized for JSON serialization.
 *
 * This serializer converts a [MetaData] instance directly to a JSON object structure,
 * avoiding the string-encoding that [StringMetaDataSerializer] uses. This ensures JSON values
 * are properly encoded without quote escaping.
 *
 * ### Supported value types
 * Each entry in the MetaData map must conform to one of the following:
 * - Primitives: [String], [Int], [Long], [Float], [Double], [Boolean]
 * - Complex types: [UUID], [Instant]
 * - Collections: [Collection], [List], [Set]
 * - Arrays: [Array]
 * - Nested Maps: [Map] with keys convertible to [String]
 *
 * ### Limitations
 * - Custom types that do not fall into the above categories will throw a [SerializationException]
 * - Deserialized non-primitive types (like [UUID], [Instant]) are restored as [String], not their original types
 *
 * This serializer is specifically optimized for JSON serialization formats.
 *
 * @author Mateusz Nowak
 * @since 4.11.2
 */
object JsonMetaDataSerializer : KSerializer<MetaData> {
    private val mapSerializer = MapSerializer(String.serializer(), JsonElement.serializer())

    override val descriptor: SerialDescriptor = mapSerializer.descriptor

    override fun serialize(encoder: Encoder, value: MetaData) {
        val jsonMap = value.entries.associate { (key, rawValue) ->
            key to toJsonElement(rawValue)
        }
        encoder.encodeSerializableValue(mapSerializer, jsonMap)
    }

    override fun deserialize(decoder: Decoder): MetaData {
        val jsonMap = decoder.decodeSerializableValue(mapSerializer)
        val reconstructed = jsonMap.mapValues { (_, jsonElement) ->
            fromJsonElement(jsonElement)
        }
        return MetaData(reconstructed)
    }

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is UUID -> JsonPrimitive(value.toString())
        is Instant -> JsonPrimitive(value.toString())
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) ->
            k.toString() to toJsonElement(v)
        })
        is Collection<*> -> JsonArray(value.map { toJsonElement(it) })
        is Array<*> -> JsonArray(value.map { toJsonElement(it) })
        else -> throw SerializationException("Unsupported type: ${value::class}")
    }

    private fun fromJsonElement(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> {
            if (element.isString) {
                element.content
            } else {
                element.booleanOrNull ?: element.intOrNull ?: element.longOrNull ?:
                element.floatOrNull ?: element.doubleOrNull ?: element.content
            }
        }
        is JsonObject -> element.mapValues { fromJsonElement(it.value) }
        is JsonArray -> element.map { fromJsonElement(it) }
    }
}