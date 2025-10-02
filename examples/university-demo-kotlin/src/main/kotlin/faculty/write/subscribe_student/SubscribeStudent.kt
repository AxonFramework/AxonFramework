package io.axoniq.demo.university.faculty.write.subscribe_student

import io.axoniq.demo.university.faculty.ids.CourseId
import io.axoniq.demo.university.shared.ids.StudentId

data class SubscribeStudent(
  val studentId: StudentId,
  val courseId: CourseId,
)
