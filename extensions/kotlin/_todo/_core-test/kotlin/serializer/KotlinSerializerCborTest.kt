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
package org.axonframework.extensions.kotlin.serializer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import org.axonframework.extensions.kotlin.serialization.KotlinSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KotlinSerializerCborTest {
    @OptIn(ExperimentalSerializationApi::class)
    private val sut = KotlinSerializer(Cbor)

    @Test
    fun testStandardList() {
        val items = listOf(
            TypeOne("a", 1),
            TypeOne("b", 2),
        )
        val actual = sut.serialize(items, ByteArray::class.java)
        val expected = "9fbf646e616d65616163666f6f01ffbf646e616d65616263666f6f02ffff"
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
        val expected = "9f9f636f6e65bf646e616d65616163666f6f01ffff9f636f6e65bf646e616d65616263666f6f02ffff9f6374776fbf646e616d656163636261729f0304ffffff9f636f6e65bf646e616d65616463666f6f05ffffff"
        assertEquals("List:org.axonframework.extensions.kotlin.serializer.SuperType", actual.type.name)
        assertEquals(expected, actual.data.toHex())
    }
}
