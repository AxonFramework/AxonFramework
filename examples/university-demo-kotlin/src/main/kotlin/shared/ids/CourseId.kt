@file:OptIn(ExperimentalStdlibApi::class)

package io.axoniq.demo.university.shared.ids

import java.util.*

@JvmExposeBoxed
@JvmInline
value class CourseId(val value: String) {
  companion object {
    fun random(): CourseId = CourseId(UUID.randomUUID().toString())
  }

  override fun toString() = value
}
