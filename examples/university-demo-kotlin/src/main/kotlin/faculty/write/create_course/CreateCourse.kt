package io.axoniq.demo.university.faculty.write.create_course

import io.axoniq.demo.university.shared.ids.CourseId
import org.axonframework.modelling.annotations.TargetEntityId

data class CreateCourse(
  val courseId: CourseId,
  val name: String,
  val capacity: Int
) {
  companion object {
    const val ID = "courseId"
  }
}
