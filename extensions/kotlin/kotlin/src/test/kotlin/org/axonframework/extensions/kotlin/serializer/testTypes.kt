package org.axonframework.extensions.kotlin.serializer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
