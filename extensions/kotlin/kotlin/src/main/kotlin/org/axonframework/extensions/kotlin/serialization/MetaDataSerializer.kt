package org.axonframework.extensions.kotlin.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.axonframework.messaging.MetaData

/**
 * Serializer for Axon Framework's [MetaData] class.
 * Since MetaData implements Map<String, Object>, we can use a map serializer
 * but need special handling for the polymorphic values.
 */
object MetaDataSerializer : KSerializer<MetaData> {
    // We use a map serializer with String keys and polymorphic Any values
    private val mapSerializer = MapSerializer(
        keySerializer = String.serializer(),
        valueSerializer = PolymorphicSerializer(Any::class).nullable
    )

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("org.axonframework.messaging.MetaData") {
        element("entries", mapSerializer.descriptor)
    }

    override fun serialize(encoder: Encoder, value: MetaData) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, mapSerializer, value)
        }
    }

    override fun deserialize(decoder: Decoder): MetaData {
        // Deserialize as a Map, then convert to MetaData
        return decoder.decodeStructure(descriptor) {
            var map: Map<String, Any?>? = null
            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                when (index) {
                    0 -> map = decodeSerializableElement(descriptor, index, mapSerializer)
                }
            }
            MetaData.from(map ?: HashMap<String, Any?>())
        }
    }
}