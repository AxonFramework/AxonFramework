import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import org.axonframework.extensions.kotlin.serialization.AxonSerializersModule
import org.axonframework.extensions.kotlin.serialization.KotlinSerializer
import org.axonframework.messaging.MetaData
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class MetaDataSerializerTest {

    private val jsonSerializer = KotlinSerializer(
        serialFormat = Json {
            serializersModule = AxonSerializersModule
        }
    )

    @OptIn(ExperimentalSerializationApi::class)
    private val cborSerializer = KotlinSerializer(
        serialFormat = Cbor {
            serializersModule = AxonSerializersModule
        }
    )

    @Test
    fun `should serialize and deserialize empty MetaData`() {
        val emptyMetaData = MetaData.emptyInstance()

        val serialized = jsonSerializer.serialize(emptyMetaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(emptyMetaData, deserialized)
        assertTrue(deserialized!!.isEmpty())
    }

    @Test
    fun `should serialize and deserialize MetaData with String values`() {
        val metaData = MetaData.with("key1", "value1")
            .and("key2", "value2")

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData, deserialized)
        assertEquals("value1", deserialized?.get("key1"))
        assertEquals("value2", deserialized?.get("key2"))
    }

    @Test
    fun `should serialize and deserialize MetaData with numeric values`() {
        val metaData = MetaData.with("int", 42)
            .and("long", 123456789L)
            .and("double", 3.14159)

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData.size, deserialized!!.size)
        // Note: numbers might be deserialized as different numeric types
        // but their string representation should match
        assertEquals(metaData["int"].toString(), deserialized.get("int").toString())
        assertEquals(metaData["long"].toString(), deserialized.get("long").toString())
        assertEquals(metaData["double"].toString(), deserialized.get("double").toString())
    }

    @Test
    fun `should serialize and deserialize MetaData with boolean values`() {
        val metaData = MetaData.with("isTrue", true)
            .and("isFalse", false)

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData, deserialized)
        assertEquals(true, deserialized?.get("isTrue"))
        assertEquals(false, deserialized?.get("isFalse"))
    }

    @Test
    fun `should serialize and deserialize MetaData with null values`() {
        val metaData = MetaData.with("nullValue", null)
            .and("nonNullValue", "present")

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData, deserialized)
        assertNull(deserialized?.get("nullValue"))
        assertEquals("present", deserialized?.get("nonNullValue"))
    }

    @Test
    fun `should handle complex objects as string representations`() {
        val now = Instant.now()
        val uuid = UUID.randomUUID()

        val metaData = MetaData.with("timestamp", now)
            .and("uuid", uuid)

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData.size, deserialized!!.size)
        // Complex objects are stored as strings
        assertEquals(now.toString(), deserialized.get("timestamp"))
        assertEquals(uuid.toString(), deserialized.get("uuid"))
    }

    @Test
    fun `should work with mixed value types`() {
        val metaData = MetaData.with("string", "text")
            .and("number", 123)
            .and("boolean", true)
            .and("null", null)

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData.size, deserialized!!.size)
        assertEquals("text", deserialized.get("string"))
        assertEquals(metaData["number"].toString(), deserialized.get("number").toString())
        assertEquals(true, deserialized.get("boolean"))
        assertNull(deserialized.get("null"))
    }

    @Test
    fun `should work with CBOR format`() {
        val metaData = MetaData.with("string", "text")
            .and("number", 123)
            .and("boolean", true)

        val serialized = cborSerializer.serialize(metaData, ByteArray::class.java)
        val deserialized = cborSerializer.deserialize<ByteArray, MetaData>(serialized)

        assertEquals(metaData.size, deserialized!!.size)
        assertEquals("text", deserialized.get("string"))
        assertEquals(metaData["number"].toString(), deserialized.get("number").toString())
        assertEquals(true, deserialized.get("boolean"))
    }

    @Test
    fun `should handle string that looks like a number or boolean`() {
        val metaData = MetaData.with("numberString", "123")
            .and("booleanString", "true")

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertNotNull(deserialized?.get("numberString"))
        assertNotNull(deserialized?.get("booleanString"))
    }

    @Test
    fun `should handle nested maps in MetaData`() {
        val nestedMap = mapOf(
            "key1" to "value1",
            "key2" to 123,
            "nested" to mapOf("a" to 1, "b" to 2)
        )

        val metaData = MetaData.with("mapValue", nestedMap)

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        val deserializedValue = deserialized!!["mapValue"]
        assertEquals(nestedMap, deserializedValue)
    }

    @Test
    fun `should handle lists in MetaData`() {
        val list = listOf("item1", "item2", "item3")

        val metaData = MetaData.with("listValue", list)

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData.size, deserialized!!.size)

        val deserializedValue = deserialized["listValue"] as List<*>
        assertTrue(deserializedValue.contains("item1"))
        assertTrue(deserializedValue.contains("item2"))
        assertTrue(deserializedValue.contains("item3"))
    }

    @Test
    fun `should handle complex nested structures in MetaData`() {
        val complexStructure = mapOf(
            "string" to "value",
            "number" to 42,
            "boolean" to true,
            "null" to null,
            "list" to listOf(1, 2, 3),
            "nestedMap" to mapOf(
                "a" to "valueA",
                "b" to listOf("x", "y", "z"),
                "c" to mapOf("nested" to "deepValue")
            )
        )

        val metaData = MetaData.with("complexValue", complexStructure)

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData.size, deserialized!!.size)

        val deserializedValue = deserialized["complexValue"] as Map<*, *>
        assertEquals(deserializedValue, complexStructure)
    }

    @Test
    fun `should handle custom objects in MetaData as Strings`() {
        data class Person(val name: String, val age: Int)

        val person = Person("John Doe", 30)

        val metaData = MetaData.with("personValue", person)

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData.size, deserialized!!.size)

        val deserializedValue = deserialized["personValue"]
        assertTrue(deserializedValue is String)
        val valueAsString = deserializedValue.toString()
        assertTrue(valueAsString.contains("John Doe"))
        assertTrue(valueAsString.contains("30"))
    }

}