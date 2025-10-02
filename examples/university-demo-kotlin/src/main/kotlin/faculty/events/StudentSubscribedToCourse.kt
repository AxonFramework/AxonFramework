package io.axoniq.demo.university.faculty.events

import io.axoniq.demo.university.faculty.Faculty.Tag
import io.axoniq.demo.university.faculty.ids.CourseId
import io.axoniq.demo.university.shared.ids.StudentId
import org.axonframework.eventsourcing.annotations.EventTag

data class StudentSubscribedToCourse(
  @EventTag(key = Tag.STUDENT_ID)
  val studentId: StudentId,
  @EventTag(key = Tag.COURSE_ID)
  val courseId: CourseId,
)
