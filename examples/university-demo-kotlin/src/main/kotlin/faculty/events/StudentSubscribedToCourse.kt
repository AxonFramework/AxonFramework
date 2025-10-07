package io.axoniq.demo.university.faculty.events

import io.axoniq.demo.university.faculty.FacultyTags
import io.axoniq.demo.university.shared.ids.CourseId
import io.axoniq.demo.university.shared.ids.StudentId
import org.axonframework.eventsourcing.annotations.EventTag

data class StudentSubscribedToCourse(
  @EventTag(key = FacultyTags.STUDENT_ID)
  val studentId: StudentId,

  @EventTag(key = FacultyTags.COURSE_ID)
  val courseId: CourseId,
)
