/*
 * Copyright (c) 2010-2025. Axon Framework
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
package org.axonframework.extensions.kotlin.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
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
import org.axonframework.messaging.responsetypes.InstanceResponseType
import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType
import org.axonframework.messaging.responsetypes.OptionalResponseType
import org.axonframework.messaging.responsetypes.ResponseType
import kotlin.reflect.KClass

/**
 * Serializer for Axon's [TrackingToken] class.
 * Provides serialization and deserialization support for nullable instances of TrackingToken.
 *
 * @see TrackingToken
 */
val trackingTokenSerializer = PolymorphicSerializer(TrackingToken::class).nullable

/**
 * Serializer for the [ReplayToken.context], represented as a nullable String.
 * This context is typically used to provide additional information during token replay operations.
 *
 * This serializer is used by [trackingTokenSerializer] to serialize the context field and now only [String] type or null value is supported!
 * Sadly enough, there's no straightforward solution to support [Any]; not without adjusting the context field of the ReplayToken in Axon Framework itself.
 * That is, however, a breaking change, and as such, cannot be done till version 5.0.0 of the Axon Framework.
 * This also allow more complex objects as the context, although it requires the user to do the de-/serialization to/from String, instead of the Axon Framework itself.
 * Look at AxonSerializersTest, case `replay token with complex object as String context` for an example how to handle that using Kotlin Serialization.
 *
 * @see ReplayToken.context
 */
val replayTokenContextSerializer = String.serializer().nullable

/**
 * Module defining serializers for Axon Framework's core event handling and messaging components.
 * This module includes serializers for TrackingTokens, ScheduleTokens, and ResponseTypes, enabling
 * seamless integration with Axon-based applications.
 */
val AxonSerializersModule = SerializersModule {
    contextual(ConfigToken::class) { ConfigTokenSerializer }
    contextual(GapAwareTrackingToken::class) { GapAwareTrackingTokenSerializer }
    contextual(MultiSourceTrackingToken::class) { MultiSourceTrackingTokenSerializer }
    contextual(MergedTrackingToken::class) { MergedTrackingTokenSerializer }
    contextual(ReplayToken::class) { ReplayTokenSerializer }
    contextual(GlobalSequenceTrackingToken::class) { GlobalSequenceTrackingTokenSerializer }
    polymorphic(TrackingToken::class) {
        subclass(ConfigTokenSerializer)
        subclass(GapAwareTrackingTokenSerializer)
        subclass(MultiSourceTrackingTokenSerializer)
        subclass(MergedTrackingTokenSerializer)
        subclass(ReplayTokenSerializer)
        subclass(GlobalSequenceTrackingTokenSerializer)
    }

    contextual(SimpleScheduleToken::class) { SimpleScheduleTokenSerializer }
    contextual(QuartzScheduleToken::class) { QuartzScheduleTokenSerializer }
    polymorphic(ScheduleToken::class) {
        subclass(SimpleScheduleTokenSerializer)
        subclass(QuartzScheduleTokenSerializer)
    }

    contextual(InstanceResponseType::class) { InstanceResponseTypeSerializer }
    contextual(OptionalResponseType::class) { OptionalResponseTypeSerializer }
    contextual(MultipleInstancesResponseType::class) { MultipleInstancesResponseTypeSerializer }
    contextual(ArrayResponseType::class) { ArrayResponseTypeSerializer }
    polymorphic(ResponseType::class) {
        subclass(InstanceResponseTypeSerializer)
        subclass(OptionalResponseTypeSerializer)
        subclass(MultipleInstancesResponseTypeSerializer)
        subclass(ArrayResponseTypeSerializer)
    }
}

/**
 * Serializer for [ConfigToken].
 *
 * @see ConfigToken
 */
object ConfigTokenSerializer : KSerializer<ConfigToken> {

    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())
    override val descriptor = buildClassSerialDescriptor(ConfigToken::class.java.name) {
        element<Map<String, String>>("config")
    }

    override fun deserialize(decoder: Decoder): ConfigToken = decoder.decodeStructure(descriptor) {
        var config: Map<String, String>? = null
        while (true) {
            val index = decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            when (index) {
                0 -> config = decodeSerializableElement(descriptor, index, mapSerializer)
            }
        }
        ConfigToken(
            config ?: throw SerializationException("Element 'config' is missing"),
        )
    }

    override fun serialize(encoder: Encoder, value: ConfigToken) = encoder.encodeStructure(descriptor) {
        encodeSerializableElement(descriptor, 0, mapSerializer, value.config)
    }
}

/**
 * Serializer for [GapAwareTrackingToken].
 *
 * @see GapAwareTrackingToken
 */
object GapAwareTrackingTokenSerializer : KSerializer<GapAwareTrackingToken> {

    private val setSerializer = SetSerializer(Long.serializer())
    override val descriptor = buildClassSerialDescriptor(GapAwareTrackingToken::class.java.name) {
        element<Long>("index")
        element("gaps", setSerializer.descriptor)
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var gapIndex: Long? = null
        var gaps: Set<Long>? = null
        while (true) {
            val index = decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            when (index) {
                0 -> gapIndex = decodeLongElement(descriptor, index)
                1 -> gaps = decodeSerializableElement(descriptor, index, setSerializer)
            }
        }
        GapAwareTrackingToken(
            gapIndex ?: throw SerializationException("Element 'gapIndex' is missing"),
            gaps ?: throw SerializationException("Element 'gaps' is missing"),
        )
    }

    override fun serialize(encoder: Encoder, value: GapAwareTrackingToken) = encoder.encodeStructure(descriptor) {
        encodeLongElement(descriptor, 0, value.index)
        encodeSerializableElement(descriptor, 1, setSerializer, value.gaps)
    }
}

/**
 * Serializer for [MultiSourceTrackingToken].
 *
 * @see MultiSourceTrackingToken
 */
object MultiSourceTrackingTokenSerializer : KSerializer<MultiSourceTrackingToken> {

    private val mapSerializer = MapSerializer(String.serializer(), trackingTokenSerializer)
    override val descriptor = buildClassSerialDescriptor(MultiSourceTrackingToken::class.java.name) {
        element<Map<String, TrackingToken>>("trackingTokens")
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var trackingTokens: Map<String, TrackingToken?>? = null
        while (true) {
            val index = decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            when (index) {
                0 -> trackingTokens = decodeSerializableElement(descriptor, index, mapSerializer)
            }
        }
        MultiSourceTrackingToken(
            trackingTokens ?: throw SerializationException("Element 'trackingTokens' is missing"),
        )
    }

    override fun serialize(encoder: Encoder, value: MultiSourceTrackingToken) = encoder.encodeStructure(descriptor) {
        encodeSerializableElement(descriptor, 0, mapSerializer, value.trackingTokens)
    }
}

/**
 * Serializer for [MergedTrackingToken].
 *
 * @see MergedTrackingToken
 */
object MergedTrackingTokenSerializer : KSerializer<MergedTrackingToken> {

    override val descriptor = buildClassSerialDescriptor(MergedTrackingToken::class.java.name) {
        element<TrackingToken>("lowerSegmentToken")
        element<TrackingToken>("upperSegmentToken")
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var lowerSegmentToken: TrackingToken? = null
        var upperSegmentToken: TrackingToken? = null
        while (true) {
            val index = decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            when (index) {
                0 -> lowerSegmentToken = decodeSerializableElement(descriptor, index, trackingTokenSerializer)
                1 -> upperSegmentToken = decodeSerializableElement(descriptor, index, trackingTokenSerializer)
            }
        }
        MergedTrackingToken(
            lowerSegmentToken ?: throw SerializationException("Element 'lowerSegmentToken' is missing"),
            upperSegmentToken ?: throw SerializationException("Element 'upperSegmentToken' is missing"),
        )
    }

    override fun serialize(encoder: Encoder, value: MergedTrackingToken) = encoder.encodeStructure(descriptor) {
        encodeSerializableElement(descriptor, 0, trackingTokenSerializer, value.lowerSegmentToken())
        encodeSerializableElement(descriptor, 1, trackingTokenSerializer, value.upperSegmentToken())
    }
}

/**
 * Serializer for [ReplayToken].
 * The [ReplayToken.context] value can be only a String or null.
 * This serializer uses [replayTokenContextSerializer] to serialize the context field and now only [String] type or null value is supported!
 *
 * @see ReplayToken
 * @see [replayTokenContextSerializer]
 */
object ReplayTokenSerializer : KSerializer<ReplayToken> {

    override val descriptor = buildClassSerialDescriptor(ReplayToken::class.java.name) {
        element<TrackingToken>("tokenAtReset")
        element<TrackingToken>("currentToken")
        element<String>("context")
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var tokenAtReset: TrackingToken? = null
        var currentToken: TrackingToken? = null
        var context: String? = null
        while (true) {
            val index = decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            when (index) {
                0 -> tokenAtReset = decodeSerializableElement(descriptor, index, trackingTokenSerializer)
                1 -> currentToken = decodeSerializableElement(descriptor, index, trackingTokenSerializer)
                2 -> context = decodeSerializableElement(descriptor, index, replayTokenContextSerializer)
            }
        }
        ReplayToken.createReplayToken(
            tokenAtReset ?: throw SerializationException("Element 'tokenAtReset' is missing"),
            currentToken,
            context
        ) as ReplayToken
    }

    override fun serialize(encoder: Encoder, value: ReplayToken) = encoder.encodeStructure(descriptor) {
        encodeSerializableElement(descriptor, 0, trackingTokenSerializer, value.tokenAtReset)
        encodeSerializableElement(descriptor, 1, trackingTokenSerializer, value.currentToken)
        encodeSerializableElement(
            descriptor,
            2,
            replayTokenContextSerializer,
            stringOrNullFrom(value.context())
        )
    }

    private fun stringOrNullFrom(obj: Any?): String? =
        obj?.takeIf { it is String }?.let { it as String }
}

/**
 * Serializer for [GlobalSequenceTrackingToken].
 *
 * @see GlobalSequenceTrackingToken
 */
object GlobalSequenceTrackingTokenSerializer : KSerializer<GlobalSequenceTrackingToken> {

    override val descriptor = buildClassSerialDescriptor(GlobalSequenceTrackingToken::class.java.name) {
        element<Long>("globalIndex")
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var globalIndex: Long? = null
        while (true) {
            val index = decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            when (index) {
                0 -> globalIndex = decodeLongElement(descriptor, index)
            }
        }
        GlobalSequenceTrackingToken(
            globalIndex ?: throw SerializationException("Element 'globalIndex' is missing"),
        )
    }

    override fun serialize(encoder: Encoder, value: GlobalSequenceTrackingToken) = encoder.encodeStructure(descriptor) {
        encodeLongElement(descriptor, 0, value.globalIndex)
    }
}

/**
 * Serializer for [SimpleScheduleToken].
 *
 * @see SimpleScheduleToken
 */
object SimpleScheduleTokenSerializer : KSerializer<SimpleScheduleToken> {

    override val descriptor = buildClassSerialDescriptor(SimpleScheduleToken::class.java.name) {
        element<String>("tokenId")
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var tokenId: String? = null
        while (true) {
            val index = decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            when (index) {
                0 -> tokenId = decodeStringElement(descriptor, index)
            }
        }
        SimpleScheduleToken(
            tokenId ?: throw SerializationException("Element 'tokenId' is missing"),
        )
    }

    override fun serialize(encoder: Encoder, value: SimpleScheduleToken) = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.tokenId)
    }
}

/**
 * Serializer for [QuartzScheduleToken].
 *
 * @see QuartzScheduleToken
 */
object QuartzScheduleTokenSerializer : KSerializer<QuartzScheduleToken> {

    override val descriptor = buildClassSerialDescriptor(QuartzScheduleToken::class.java.name) {
        element<String>("jobIdentifier")
        element<String>("groupIdentifier")
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var jobIdentifier: String? = null
        var groupIdentifier: String? = null
        while (true) {
            val index = decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            when (index) {
                0 -> jobIdentifier = decodeStringElement(descriptor, index)
                1 -> groupIdentifier = decodeStringElement(descriptor, index)
            }
        }
        QuartzScheduleToken(
            jobIdentifier ?: throw SerializationException("Element 'jobIdentifier' is missing"),
            groupIdentifier ?: throw SerializationException("Element 'groupIdentifier' is missing"),
        )
    }

    override fun serialize(encoder: Encoder, value: QuartzScheduleToken) = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.jobIdentifier)
        encodeStringElement(descriptor, 1, value.groupIdentifier)
    }
}

abstract class ResponseTypeSerializer<R : ResponseType<*>>(kClass: KClass<R>, private val factory: (Class<*>) -> R) : KSerializer<R> {

    override val descriptor = buildClassSerialDescriptor(kClass.java.name) {
        element<String>("expectedResponseType")
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var expectedResponseType: Class<*>? = null
        while (true) {
            val index = decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            when (index) {
                0 -> expectedResponseType = Class.forName(decodeStringElement(descriptor, index))
            }
        }
        factory(
            expectedResponseType ?: throw SerializationException("Element 'expectedResponseType' is missing")
        )
    }

    override fun serialize(encoder: Encoder, value: R) = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.expectedResponseType.name)
    }
}

/**
 * Serializer for [InstanceResponseType].
 *
 * @see InstanceResponseType
 */
object InstanceResponseTypeSerializer : KSerializer<InstanceResponseType<*>>,
    ResponseTypeSerializer<InstanceResponseType<*>>(InstanceResponseType::class, { InstanceResponseType(it) })

/**
 * Serializer for [OptionalResponseType].
 *
 * @see OptionalResponseType
 */
object OptionalResponseTypeSerializer : KSerializer<OptionalResponseType<*>>,
    ResponseTypeSerializer<OptionalResponseType<*>>(OptionalResponseType::class, { OptionalResponseType(it) })

/**
 * Serializer for [MultipleInstancesResponseType].
 *
 * @see MultipleInstancesResponseType
 */
object MultipleInstancesResponseTypeSerializer : KSerializer<MultipleInstancesResponseType<*>>,
    ResponseTypeSerializer<MultipleInstancesResponseType<*>>(MultipleInstancesResponseType::class, { MultipleInstancesResponseType(it) })

/**
 * Serializer for [ArrayResponseType].
 *
 * @see ArrayResponseType
 */
object ArrayResponseTypeSerializer : KSerializer<ArrayResponseType<*>>,
    ResponseTypeSerializer<ArrayResponseType<*>>(ArrayResponseType::class, { ArrayResponseType(it) })
