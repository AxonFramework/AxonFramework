package io.axoniq.demo.university.faculty.write.subscribe_student

import io.axoniq.demo.university.faculty.FacultyTags
import io.axoniq.demo.university.faculty.events.CourseCreated
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse
import io.axoniq.demo.university.shared.ids.CourseId
import io.axoniq.demo.university.shared.ids.StudentId
import io.axoniq.demo.university.shared.ids.SubscriptionId
import org.axonframework.eventsourcing.annotations.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotations.EventSourcedEntity
import org.axonframework.eventsourcing.annotations.EventSourcingHandler
import org.axonframework.eventsourcing.annotations.reflection.EntityCreator
import org.axonframework.eventstreaming.EventCriteria
import org.axonframework.eventstreaming.Tag

@EventSourcedEntity
class SubscribeStudentToCourseState @EntityCreator constructor() {
  companion object {

    @JvmStatic
    @EventCriteriaBuilder
    fun resolveCriteria(id: SubscriptionId): EventCriteria = EventCriteria.either(
      EventCriteria.havingTags(Tag.of(FacultyTags.COURSE_ID, id.courseId.toString()))
        .andBeingOneOfTypes(
          CourseCreated::class.java.getName(),
          StudentSubscribedToCourse::class.java.getName(),
        ),
      EventCriteria
        .havingTags(Tag.of(FacultyTags.STUDENT_ID, id.studentId.toString()))
        .andBeingOneOfTypes(
          StudentEnrolledInFaculty::class.java.getName(),
          StudentSubscribedToCourse::class.java.getName(),
        )
    )
  }

  var courseId: CourseId? = null
  var capacity: Int = 0
  var studentsInCourse: Int = 0

  var studentId: StudentId? = null
  var courseForStudent: Int = 0
  var alreadySubscribed: Boolean = false

  @EventSourcingHandler
  fun evolve(event: CourseCreated) = apply {
    courseId = event.courseId
    capacity = event.capacity
  }

  @EventSourcingHandler
  fun evolve(event: StudentEnrolledInFaculty) = apply {
    studentId = event.studentId
  }

  @EventSourcingHandler
  fun evolve(event: StudentSubscribedToCourse) = apply {
    if (event.courseId == courseId) {
      studentsInCourse++
    }
    if (event.studentId == studentId) {
      courseForStudent++
    }

    alreadySubscribed = event.studentId == studentId && event.courseId == courseId
  }
}
