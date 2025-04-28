package org.axonframework.extensions.kotlin.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import org.axonframework.messaging.MetaData

/**
 * A serializer for Axon's MetaData class that works with any encoder format.
 * MetaData is essentially a map that can contain primitive values and custom objects.
 *
 * This serializer uses a two-step approach:
 * 1. First, we convert the MetaData to a serializable representation using JSON as an intermediate format
 * 2. Then we use the target encoder to write the final output
 *
 * This approach allows us to properly handle any type of value in the MetaData, including custom objects.
 *
 * @since 4.11.1
 */
object MetaDataSerializer : KSerializer<MetaData> {
    // We use Json as an intermediary to convert arbitrary objects to a serializable form
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    // Map with String keys and JsonElement values (which can represent any value)
    private val mapSerializer = MapSerializer(String.serializer(), JsonElement.serializer())

    override val descriptor: SerialDescriptor = mapSerializer.descriptor

    override fun serialize(encoder: Encoder, value: MetaData) {
        // Convert each value to JsonElement, which can represent any serializable value
        val jsonMap = value.mapValues { (_, value) ->
            convertToJsonElement(value)
        }

        // Serialize the map of JsonElements
        mapSerializer.serialize(encoder, jsonMap)
    }

    override fun deserialize(decoder: Decoder): MetaData {
        // First deserialize to a Map<String, JsonElement>
        val jsonMap = mapSerializer.deserialize(decoder)

        // Then convert each JsonElement back to its original type
        val resultMap = jsonMap.mapValues { (_, jsonElement) ->
            convertFromJsonElement(jsonElement)
        }

        return MetaData.from(resultMap)
    }

    /**
     * Convert any value to a JsonElement
     */
    private fun convertToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is JsonElement -> value
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Enum<*> -> JsonPrimitive(value.name)
            // For custom objects, use Json to encode them to a JsonElement
            else -> try {
                json.encodeToJsonElement(value)
            } catch (e: Exception) {
                // If serialization fails, fall back to string representation
                JsonPrimitive(value.toString())
            }
        }
    }

    /**
     * Convert a JsonElement back to its original type
     */
    private fun convertFromJsonElement(element: JsonElement): Any? {
        return when (element) {
            is JsonNull -> null
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.boolean
                element.longOrNull != null -> element.long
                element.doubleOrNull != null -> element.double
                else -> element.content
            }
            is JsonArray -> element.map { convertFromJsonElement(it) }
            is JsonObject -> element.mapValues { (_, value) -> convertFromJsonElement(value) }
        }
    }

    /**
     * Extension method to simplify JSON encoding of arbitrary values
     */
    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    private fun Json.encodeToJsonElement(value: Any): JsonElement {
        return when (value) {
            is JsonElement -> value
            else -> {
                try {
                    // Try to find a serializer for this type
                    val serializer = serializersModule.getContextual(value::class)
                        ?: value::class.serializer()

                    @Suppress("UNCHECKED_CAST")
                    encodeToJsonElement(serializer as KSerializer<Any>, value)
                } catch (e: Exception) {
                    // If we can't serialize it properly, convert to string
                    JsonPrimitive(value.toString())
                }
            }
        }
    }
}