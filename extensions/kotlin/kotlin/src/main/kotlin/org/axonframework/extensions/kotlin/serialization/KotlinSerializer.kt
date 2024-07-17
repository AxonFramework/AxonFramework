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
package org.axonframework.extensions.kotlin.serialization

import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.SerializationException
import kotlinx.serialization.StringFormat
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
import org.axonframework.serialization.AnnotationRevisionResolver
import org.axonframework.serialization.ChainingConverter
import org.axonframework.serialization.Converter
import org.axonframework.serialization.RevisionResolver
import org.axonframework.serialization.SerializedObject
import org.axonframework.serialization.SerializedType
import org.axonframework.serialization.Serializer
import org.axonframework.serialization.SimpleSerializedObject
import org.axonframework.serialization.SimpleSerializedType
import org.axonframework.serialization.UnknownSerializedType
import java.util.concurrent.ConcurrentHashMap
import org.axonframework.serialization.SerializationException as AxonSerializationException

/**
 * Implementation of Axon Serializer that uses a kotlinx.serialization implementation.
 *
 * @see kotlinx.serialization.Serializer
 * @see org.axonframework.serialization.Serializer
 *
 * @since 4.10.0
 * @author Gerard de Leeuw
 */
class KotlinSerializer(
    private val serialFormat: SerialFormat,
    private val revisionResolver: RevisionResolver = AnnotationRevisionResolver(),
    private val converter: Converter = ChainingConverter(),
) : Serializer {

    private val serializerCache: MutableMap<Class<*>, KSerializer<*>> = ConcurrentHashMap()
    private val unknownSerializedType = UnknownSerializedType::class.java

    override fun getConverter(): Converter = converter

    override fun <T> canSerializeTo(expectedRepresentation: Class<T>): Boolean = when (serialFormat) {
        is StringFormat -> converter.canConvert(String::class.java, expectedRepresentation)
        is BinaryFormat -> converter.canConvert(ByteArray::class.java, expectedRepresentation)
        else -> false
    }

    override fun <T> serialize(value: Any?, expectedRepresentation: Class<T>): SerializedObject<T> {
        try {
            val serializedType = typeForValue(value)
            val classForType = classForType(serializedType)

            val serializer = serializerFor(classForType, serializedType)
            val serialized: SerializedObject<*> = when (serialFormat) {
                is StringFormat -> SimpleSerializedObject(
                    serialFormat.encodeToString(serializer, value),
                    String::class.java,
                    serializedType
                )

                is BinaryFormat -> SimpleSerializedObject(
                    serialFormat.encodeToByteArray(serializer, value),
                    ByteArray::class.java,
                    serializedType
                )

                else -> throw SerializationException("Unsupported serial format: $serialFormat")
            }

            return converter.convert(serialized, expectedRepresentation)
        } catch (ex: SerializationException) {
            throw AxonSerializationException("Cannot serialize type ${value?.javaClass?.name} to representation $expectedRepresentation.", ex)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <S, T> deserialize(serializedObject: SerializedObject<S>): T? {
        try {
            if (serializedObject.type == SerializedType.emptyType()) {
                return null
            }

            val classForType = classForType(serializedObject.type)
            if (unknownSerializedType.isAssignableFrom(classForType)) {
                return UnknownSerializedType(this, serializedObject) as T
            }

            val serializer = serializerFor(classForType, serializedObject.type) as KSerializer<T>
            return when (serialFormat) {
                is StringFormat -> {
                    val json = converter.convert(serializedObject, String::class.java).data
                    when {
                        json.isEmpty() -> null
                        else -> serialFormat.decodeFromString(serializer, json)
                    }
                }

                is BinaryFormat -> {
                    val bytes = converter.convert(serializedObject, ByteArray::class.java).data
                    when {
                        bytes.isEmpty() -> null
                        else -> serialFormat.decodeFromByteArray(serializer, bytes)
                    }
                }

                else -> throw SerializationException("Unsupported serial format: $serialFormat")
            }
        } catch (ex: SerializationException) {
            throw AxonSerializationException("Could not deserialize from content type ${serializedObject.contentType} to type ${serializedObject.type}", ex)
        }
    }

    override fun classForType(type: SerializedType): Class<*> = when (val unwrapped = type.unwrap()) {
        SerializedType.emptyType() -> Void.TYPE
        else -> try {
            Class.forName(unwrapped.name)
        } catch (ex: ClassNotFoundException) {
            unknownSerializedType
        }
    }

    override fun typeForClass(type: Class<*>?): SerializedType =
        if (type == null || Void.TYPE == type || Void::class.java.isAssignableFrom(type)) {
            SimpleSerializedType.emptyType()
        } else {
            SimpleSerializedType(type.name, revisionResolver.revisionOf(type))
        }

    private fun typeForValue(value: Any?): SerializedType = when (value) {
        null -> SimpleSerializedType.emptyType()
        Unit -> SimpleSerializedType.emptyType()
        is Class<*> -> typeForClass(value)
        is Array<*> -> typeForClass(value.javaClass.componentType).wrap("Array")
        is List<*> -> typeForClass(value.findCommonInterfaceOfEntries()).wrap("List")
        is Set<*> -> typeForClass(value.findCommonInterfaceOfEntries()).wrap("Set")
        else -> typeForClass(value.javaClass)
    }

    private fun SerializedType.wrap(wrapper: String) = SimpleSerializedType("$wrapper:$name", revision)
    private fun SerializedType.unwrap() = SimpleSerializedType(name.substringAfter(':'), revision)

    @Suppress("UNCHECKED_CAST", "OPT_IN_USAGE")
    private fun serializerFor(type: Class<*>, serializedType: SerializedType): KSerializer<Any?> {
        val serializer = when {
            Void.TYPE == type || Void::class.java.isAssignableFrom(type) -> Unit.serializer()
            unknownSerializedType.isAssignableFrom(type) -> throw ClassNotFoundException("Can't load class for type $this")
            else -> serializerCache.computeIfAbsent(type, serialFormat.serializersModule::serializer)
        } as KSerializer<Any?>

        return when (serializedType.name.substringBefore(':')) {
            "Array" -> ArraySerializer(serializer)
            "List" -> ListSerializer(serializer)
            "Set" -> SetSerializer(serializer)
            else -> serializer
        } as KSerializer<Any?>
    }
}

private fun Iterable<*>.findCommonInterfaceOfEntries(): Class<*>? {
    val firstElement = firstOrNull() ?: return null

    val commonTypeInIterable = fold(firstElement.javaClass as Class<*>) { resultingClass, element ->
        if (element == null) {
            // Element is null, so we just assume the type of the previous elements
            resultingClass
        } else if (resultingClass.isAssignableFrom(element.javaClass)) {
            // Element matches the elements we've seen so far, so we can keep the type
            resultingClass
        } else {
            // Element does not match the elements we've seen so far, so we look for a common
            // interface and continue with that
            element.javaClass.interfaces.firstOrNull { it.isAssignableFrom(resultingClass) }
                ?: throw SerializationException("Cannot find interface for serialization")
        }
    }

    return commonTypeInIterable
}
