import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*
import org.axonframework.messaging.MetaData

/**
 * Serializer for Axon's MetaData class.
 *
 * This serializer handles the MetaData by converting it to a simple string-to-string
 * map for serialization. This ensures compatibility with any Kotlin serialization format.
 */
object MetaDataSerializer : KSerializer<MetaData> {
    // Use a simple String -> String map for maximum compatibility
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    override val descriptor = buildClassSerialDescriptor(MetaData::class.java.name) {
        element<Map<String, String>>("values")
    }

    override fun deserialize(decoder: Decoder): MetaData = decoder.decodeStructure(descriptor) {
        var stringMap: Map<String, String>? = null
        while (true) {
            val index = decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            when (index) {
                0 -> stringMap = decodeSerializableElement(descriptor, index, mapSerializer)
            }
        }

        if (stringMap == null || stringMap.isEmpty()) {
            return@decodeStructure MetaData.emptyInstance()
        }

        // The string map contains string representations of values
        // Convert back to appropriate types when possible
        val resultMap = HashMap<String, Any?>()
        stringMap.forEach { (key, valueStr) ->
            resultMap[key] = convertStringToTypedValue(valueStr)
        }

        return@decodeStructure MetaData.from(resultMap)
    }

    override fun serialize(encoder: Encoder, value: MetaData) {
        // Convert MetaData entries to strings
        val stringMap = HashMap<String, String>()

        value.forEach { (key, obj) ->
            stringMap[key] = obj?.toString() ?: "null"
        }

        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, mapSerializer, stringMap)
        }
    }

    /**
     * Converts a string representation back to a typed value when possible.
     */
    private fun convertStringToTypedValue(value: String): Any? {
        if (value == "null") return null

        // Try to convert to common primitive types
        return value.toBooleanStrictOrNull() ?:
        value.toIntOrNull() ?:
        value.toLongOrNull() ?:
        value.toDoubleOrNull() ?:
        // If all else fails, keep as string
        value
    }
}