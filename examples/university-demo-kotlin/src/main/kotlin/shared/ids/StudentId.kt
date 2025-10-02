@file:OptIn(ExperimentalStdlibApi::class)

package io.axoniq.demo.university.shared.ids

import java.util.*

@JvmExposeBoxed
@JvmInline
value class StudentId(val value: String) {
  companion object {
    fun random() = StudentId(UUID.randomUUID().toString())
  }

  override fun toString() = value
}

