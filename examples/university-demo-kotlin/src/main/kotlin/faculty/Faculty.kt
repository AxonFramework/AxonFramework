package io.axoniq.demo.university.faculty

import io.axoniq.demo.university.shared.CourseId
import org.axonframework.eventsourcing.annotations.EventTag

data object Faculty {
  data object Tag {
    const val STUDENT_ID: String = "studentId"
    const val COURSE_ID: String = "courseId"
  }

  sealed interface Event {
    data class CourseCreated(
      @EventTag(key = Tag.COURSE_ID)
      val courseId: CourseId,
      val name: String,
      val capacity: Int
    ) : Event
  }
}
