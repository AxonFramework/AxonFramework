package io.axoniq.demo.university.shared.ids

import io.axoniq.demo.university.faculty.FacultyTags
import org.axonframework.eventstreaming.Tag

data class SubscriptionId(
  val studentId: StudentId,
  val courseId: CourseId,
) {
  fun studentTag() = Tag.of(FacultyTags.STUDENT, studentId.toString())
  fun courseIdTag() = Tag.of(FacultyTags.COURSE, courseId.toString())
}


