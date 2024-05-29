package org.axonframework.extensions.kotlin.serializer

// copied from https://www.baeldung.com/kotlin/byte-arrays-to-hex-strings#1-formatter
fun ByteArray.toHex(): String =
    joinToString("") { eachByte -> "%02x".format(eachByte) }