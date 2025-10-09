package io.axoniq.demo.university.faculty.events

import io.axoniq.demo.university.faculty.FacultyTags
import io.axoniq.demo.university.shared.ids.StudentId
import org.axonframework.eventsourcing.annotations.EventTag

data class StudentEnrolledInFaculty(
  @EventTag(key = FacultyTags.STUDENT)
  val studentId: StudentId,
  val firstName: String,
  val lastName: String,
) : FacultyEvent
