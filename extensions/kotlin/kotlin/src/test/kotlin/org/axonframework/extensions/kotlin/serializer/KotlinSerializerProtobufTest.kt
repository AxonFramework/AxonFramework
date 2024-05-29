package org.axonframework.extensions.kotlin.serializer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.axonframework.extensions.kotlin.serialization.KotlinSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KotlinSerializerProtobufTest {
    @OptIn(ExperimentalSerializationApi::class)
    private val sut = KotlinSerializer(ProtoBuf)

    @Test
    fun testStandardList() {
        val items = listOf(
            TypeOne("a", 1),
            TypeOne("b", 2),
        )
        val actual = sut.serialize(items, ByteArray::class.java)
        val expected = "02050a01611001050a01621002"
        assertEquals("List:org.axonframework.extensions.kotlin.serializer.TypeOne", actual.type.name)
        assertEquals(expected, actual.data.toHex())
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
        val expected = "040c0a036f6e6512050a016110010c0a036f6e6512050a016210020e0a0374776f12070a0163100310040c0a036f6e6512050a01641005"
        assertEquals("List:org.axonframework.extensions.kotlin.serializer.SuperType", actual.type.name)
        assertEquals(expected, actual.data.toHex())
    }
}
