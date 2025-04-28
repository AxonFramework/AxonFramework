package org.axonframework.extensions.kotlin.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.axonframework.messaging.MetaData
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MetaDataSerializerTest {

    // Custom object to test serialization of complex types in MetaData
    @Serializable
    data class CustomObject(val name: String, val value: Int, val active: Boolean = true)

    // Extended module that includes our custom type serializer
    private val extendedModule = AxonSerializersModule + SerializersModule {
        // Correctly register the custom type for polymorphic serialization
        polymorphic(Any::class) {
            subclass(CustomObject::class)
        }
    }

    // Configure JSON with our serializers
    private val json = Json {
        serializersModule = extendedModule
        encodeDefaults = true
        // Important for polymorphic serialization
        classDiscriminator = "type"
    }

    // Create a serializer with our configuration
    private val serializer = KotlinSerializer(serialFormat = json)

    @Test
    fun `serialize empty metadata`() {
        val metadata = MetaData.emptyInstance()

        val serialized = serializer.serialize(metadata, String::class.java)

        // Verify serialization format
        assertEquals("{\"entries\":{}}", serialized.data)

        // Verify deserialization - specifying type arguments explicitly
        val deserialized = serializer.deserialize<String, MetaData>(serialized)!!
        assertEquals(metadata, deserialized)
        assertTrue(deserialized.isEmpty())
    }

    @Test
    fun `serialize metadata with primitive values`() {
        val metadata = MetaData.with("stringKey", "stringValue")
            .and("intKey", 42)
            .and("booleanKey", true)
            .and("doubleKey", 3.14)
            .and("nullKey", null)

        val serialized = serializer.serialize(metadata, String::class.java)

        // Verify deserialization - specifying type arguments explicitly
        val deserialized = serializer.deserialize<String, MetaData>(serialized)!!

        assertEquals(metadata.size, deserialized.size)
        assertEquals("stringValue", deserialized["stringKey"])
        assertEquals(42, deserialized["intKey"])
        assertEquals(true, deserialized["booleanKey"])
        assertEquals(3.14, deserialized["doubleKey"])
        assertNull(deserialized["nullKey"])
    }
//
//    @Test
//    fun `serialize metadata with collection values`() {
//        val metadata = MetaData.with("listKey", listOf("item1", "item2", "item3"))
//            .and("mapKey", mapOf("key1" to "value1", "key2" to "value2"))
//            .and("nestedListKey", listOf(listOf(1, 2), listOf(3, 4)))
//
//        val serialized = serializer.serialize(metadata, String::class.java)
//
//        // Verify deserialization - specifying type arguments explicitly
//        val deserialized = serializer.deserialize<String, MetaData>(serialized)
//
//        assertEquals(metadata.size, deserialized.size)
//
//        @Suppress("UNCHECKED_CAST")
//        val list = deserialized["listKey"] as List<String>
//        assertEquals(3, list.size)
//        assertEquals("item1", list[0])
//
//        @Suppress("UNCHECKED_CAST")
//        val map = deserialized["mapKey"] as Map<String, String>
//        assertEquals(2, map.size)
//        assertEquals("value1", map["key1"])
//
//        @Suppress("UNCHECKED_CAST")
//        val nestedList = deserialized["nestedListKey"] as List<List<Int>>
//        assertEquals(2, nestedList.size)
//        assertEquals(1, nestedList[0][0])
//    }
//
//    @Test
//    fun `serialize metadata with custom object`() {
//        val customObject = CustomObject("test", 123)
//        val metadata = MetaData.with("customKey", customObject)
//
//        val serialized = serializer.serialize(metadata, String::class.java)
//
//        // Verify deserialization - specifying type arguments explicitly
//        val deserialized = serializer.deserialize<String, MetaData>(serialized)
//
//        assertEquals(metadata.size, deserialized.size)
//
//        // With proper polymorphic serialization, we should get back our CustomObject
//        val deserializedObject = deserialized["customKey"] as CustomObject
//        assertEquals("test", deserializedObject.name)
//        assertEquals(123, deserializedObject.value)
//        assertEquals(true, deserializedObject.active)
//    }
//
//    @Test
//    fun `verify metadata works with different formats`() {
//        // Test with CBOR format
//        val cborSerializer = KotlinSerializer(
//            serialFormat = Cbor {
//                serializersModule = extendedModule
//            }
//        )
//
//        val metadata = MetaData.with("key", "value")
//            .and("customKey", CustomObject("cbor-test", 456))
//
//        val serialized = cborSerializer.serialize(metadata, ByteArray::class.java)
//        val deserialized = cborSerializer.deserialize<ByteArray, MetaData>(serialized)
//
//        assertEquals("value", deserialized["key"])
//
//        // With proper polymorphic serialization in CBOR format
//        val deserializedObject = deserialized["customKey"] as CustomObject
//        assertEquals("cbor-test", deserializedObject.name)
//        assertEquals(456, deserializedObject.value)
//    }
//
//    @Test
//    fun `verify direct serialization and deserialization`() {
//        val metadata = MetaData.with("key", "direct-serialization")
//            .and("number", 789)
//
//        // Direct serialization with the serializer module
//        val serializedString = json.encodeToString(MetaDataSerializer, metadata)
//
//        // Direct deserialization
//        val deserialized = json.decodeFromString(MetaDataSerializer, serializedString)
//
//        assertEquals(metadata, deserialized)
//        assertEquals("direct-serialization", deserialized["key"])
//        assertEquals(789, deserialized["number"])
//    }
}