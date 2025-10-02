package io.axoniq.demo.university.faculty.write.create_course

import io.axoniq.demo.university.faculty.ids.CourseId
import org.axonframework.modelling.annotations.TargetEntityId

data class CreateCourse(
  @TargetEntityId
  val courseId: CourseId,
  val name: String,
  val capacity: Int
)
