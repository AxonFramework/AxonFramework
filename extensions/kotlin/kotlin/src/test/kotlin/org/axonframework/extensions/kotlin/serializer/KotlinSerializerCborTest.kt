package org.axonframework.extensions.kotlin.serializer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import org.axonframework.extensions.kotlin.serialization.KotlinSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.HexFormat

class KotlinSerializerCborTest {
    @OptIn(ExperimentalSerializationApi::class)
    private val sut = KotlinSerializer(Cbor)
    private val format = HexFormat.of()

    @Test
    fun testStandardList() {
        val items = listOf(
            TypeOne("a", 1),
            TypeOne("b", 2),
        )
        val actual = sut.serialize(items, ByteArray::class.java)
        val expected = "9fbf646e616d65616163666f6f01ffbf646e616d65616263666f6f02ffff"
        assertEquals("List:org.axonframework.extensions.kotlin.serializer.TypeOne", actual.type.name)
        assertEquals(expected, format.formatHex(actual.data))
    }

    @Test
    fun testPolymorphicList() {
        val items = listOf(
            TypeOne("a", 1),
            TypeOne("b", 2),
            TypeTwo("c", listOf(3, 4)),
            TypeOne("d", 5),
        )
        val actual = sut.serialize(items, ByteArray::class.java)
        val expected = "9f9f636f6e65bf646e616d65616163666f6f01ffff9f636f6e65bf646e616d65616263666f6f02ffff9f6374776fbf646e616d656163636261729f0304ffffff9f636f6e65bf646e616d65616463666f6f05ffffff"
        assertEquals("List:org.axonframework.extensions.kotlin.serializer.SuperType", actual.type.name)
        assertEquals(expected, format.formatHex(actual.data))
    }
}
