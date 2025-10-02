package io.axoniq.demo.university.faculty.write.enroll_student

import io.axoniq.demo.university.faculty.Faculty.Tag
import io.axoniq.demo.university.shared.ids.StudentId
import org.axonframework.eventsourcing.annotations.EventTag

data class EnrollStudent(
  val studentId: StudentId,
  val firstName: String,
  val lastName: String,
)
