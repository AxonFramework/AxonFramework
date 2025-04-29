package org.axonframework.extensions.kotlin.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import org.axonframework.messaging.MetaData
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * A Kotlinx [KSerializer] for Axon Framework's [MetaData] type, supporting serialization across any format.
 *
 * This serializer converts a [MetaData] instance to a JSON-encoded [String] using a recursive conversion
 * of all entries into [JsonElement]s. This JSON string is then serialized using [String.serializer],
 * ensuring compatibility with any [kotlinx.serialization.encoding.Encoder]—including formats such as JSON, CBOR, ProtoBuf, or Avro.
 *
 * ### Supported value types
 * Each entry in the MetaData map must conform to one of the following:
 * - Primitives: [String], [Int], [Long], [Float], [Double], [Boolean]
 * - Temporal types: [UUID], [Date], [Instant]
 * - Collections: [Collection], [List], [Set]
 * - Arrays: [Array]
 * - Nested Maps: [Map] with keys convertible to [String]
 *
 * ### Limitations
 * - Custom types that do not fall into the above categories will throw a [SerializationException]
 * - Deserialized non-primitive types (like [UUID], [Instant], [Date]) are restored as [String], not their original types
 *
 * This serializer guarantees structural integrity of nested metadata (e.g. map within list within map), while remaining format-agnostic.
 *
 * @author Mateusz Nowak
 * @since 4.11.1
 */
object MetaDataSerializer : KSerializer<MetaData> {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun serialize(encoder: Encoder, value: MetaData) {
        val map: Map<String, JsonElement> = value.entries.associate { (key, rawValue) ->
            key to toJsonElement(rawValue)
        }
        val jsonString = json.encodeToString(MapSerializer(String.serializer(), JsonElement.serializer()), map)
        encoder.encodeSerializableValue(String.serializer(), jsonString)
    }

    override fun deserialize(decoder: Decoder): MetaData {
        val jsonString = decoder.decodeSerializableValue(String.serializer())
        val map = json.decodeFromString(MapSerializer(String.serializer(), JsonElement.serializer()), jsonString)
        val reconstructed = map.mapValues { (_, jsonElement) -> fromJsonElement(jsonElement) }
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
        is Date -> JsonPrimitive(value.toString())
        is Instant -> JsonPrimitive(value.toString())
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJsonElement(v) })
        is Collection<*> -> JsonArray(value.map { toJsonElement(it) })
        is Array<*> -> JsonArray(value.map { toJsonElement(it) })
        else -> throw SerializationException("Unsupported type: ${value::class}")
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