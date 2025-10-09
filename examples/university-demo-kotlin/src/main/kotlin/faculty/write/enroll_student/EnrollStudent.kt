package io.axoniq.demo.university.faculty.write.enroll_student

import io.axoniq.demo.university.shared.ids.StudentId
import org.axonframework.modelling.annotations.TargetEntityId

data class EnrollStudent(
  @TargetEntityId
  val studentId: StudentId,
  val firstName: String,
  val lastName: String,
) {
  companion object {
    const val ID_PROP = "studentId"
  }
}
