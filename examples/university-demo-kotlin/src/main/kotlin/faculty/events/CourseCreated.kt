package io.axoniq.demo.university.faculty.events

import io.axoniq.demo.university.faculty.Faculty.Tag
import io.axoniq.demo.university.faculty.ids.CourseId
import org.axonframework.eventsourcing.annotations.EventTag

data class CourseCreated(
  @EventTag(key = Tag.COURSE_ID)
  val courseId: CourseId,
  val name: String,
  val capacity: Int
) : FacultyEvent
