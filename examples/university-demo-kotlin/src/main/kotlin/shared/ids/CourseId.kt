@file:OptIn(ExperimentalStdlibApi::class)

package org.axonframework.examples.university.shared.ids

import java.util.*

@JvmExposeBoxed
@JvmInline
value class CourseId(val value: String) {
    companion object {
        fun random(): CourseId = CourseId(UUID.randomUUID().toString())
    }

    override fun toString() = value
}
