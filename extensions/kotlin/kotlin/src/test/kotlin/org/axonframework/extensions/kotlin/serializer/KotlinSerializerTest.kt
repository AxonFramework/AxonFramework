package org.axonframework.extensions.kotlin.serializer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.axonframework.extensions.kotlin.serialization.KotlinSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KotlinSerializerTest {
    private val sut = KotlinSerializer(Json)

    @Test
    fun testStandardList() {
        val items = listOf(
            TypeOne("a", 1),
            TypeOne("b", 2),
        )
        val actual = sut.serialize(items, String::class.java)
        val expected = """[{"name":"a","foo":1},{"name":"b","foo":2}]"""
        assertEquals("List:org.axonframework.extensions.kotlin.serializer.KotlinSerializerTest\$TypeOne", actual.type.name)
        assertEquals(expected, actual.data)
    }

    @Test
    fun testPolymorphicList() {
        val items = listOf(
            TypeOne("a", 1),
            TypeOne("b", 2),
            TypeTwo("c", listOf(3, 4)),
            TypeOne("d", 5),
        )
        val actual = sut.serialize(items, String::class.java)
        val expected = """[{"type":"one","name":"a","foo":1},{"type":"one","name":"b","foo":2},{"type":"two","name":"c","bar":[3,4]},{"type":"one","name":"d","foo":5}]"""
        assertEquals("List:org.axonframework.extensions.kotlin.serializer.KotlinSerializerTest\$SuperType", actual.type.name)
        assertEquals(expected, actual.data)
    }

    @Serializable
    sealed interface SuperType {
        val name: String
    }

    @Serializable
    @SerialName("one")
    data class TypeOne(
        override val name: String,
        val foo: Int,
    ) : SuperType

    @Serializable
    @SerialName("two")
    data class TypeTwo(
        override val name: String,
        val bar: List<Int>,
    ) : SuperType

}
