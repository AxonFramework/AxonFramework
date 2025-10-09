package io.axoniq.demo.university.faculty.write.subscribe_student

import io.axoniq.demo.university.faculty.FacultyTags
import io.axoniq.demo.university.faculty.events.CourseCreated
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse
import io.axoniq.demo.university._ext.conditionalEvolve
import io.axoniq.demo.university.shared.ids.CourseId
import io.axoniq.demo.university.shared.ids.StudentId
import io.axoniq.demo.university.shared.ids.SubscriptionId
import org.axonframework.eventsourcing.annotations.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotations.EventSourcedEntity
import org.axonframework.eventsourcing.annotations.EventSourcingHandler
import org.axonframework.eventsourcing.annotations.reflection.EntityCreator
import org.axonframework.eventstreaming.EventCriteria
import org.axonframework.eventstreaming.Tag
import org.axonframework.messaging.ClassBasedMessageTypeResolver

@EventSourcedEntity
internal class State @EntityCreator constructor() {
  companion object {

    const val MAX_COURSES_PER_STUDENT = 3
    val CLASS_MESSAGE_TYPE_RESOLVER = ClassBasedMessageTypeResolver()

    @JvmStatic
    @EventCriteriaBuilder
    fun resolveCriteria(id: SubscriptionId): EventCriteria = EventCriteria.either(
      EventCriteria
        .havingTags(Tag.of(FacultyTags.COURSE, id.courseId.toString()))
        .andBeingOneOfTypes(
          CLASS_MESSAGE_TYPE_RESOLVER,
          CourseCreated::class.java,
          StudentSubscribedToCourse::class.java,
        ),
      EventCriteria
        .havingTags(Tag.of(FacultyTags.STUDENT, id.studentId.toString()))
        .andBeingOneOfTypes(
          CLASS_MESSAGE_TYPE_RESOLVER,
          StudentEnrolledInFaculty::class.java,
          StudentSubscribedToCourse::class.java,
        )
    )
  }

  var courseId: CourseId? = null
  var capacity: Int = 0
  var studentsInCourse: Int = 0

  var studentId: StudentId? = null
  var coursesForStudent: Int = 0
  var alreadySubscribed: Boolean = false

  fun decide(cmd: SubscribeStudentToCourse): List<Any> {
    check(this.studentId != null) { "Student with given id never enrolled the faculty" }
    check(this.courseId != null) { "Course with given id does not exist" }
    check(this.capacity > this.studentsInCourse) { "Course is fully booked" }
    check(this.coursesForStudent < MAX_COURSES_PER_STUDENT) { "Student subscribed to too many courses" }
    check(!this.alreadySubscribed) { "Student already subscribed to this course" }
    return listOf(StudentSubscribedToCourse(cmd.studentId, cmd.courseId))
  }

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
  fun evolve(event: StudentSubscribedToCourse) =
    apply { alreadySubscribed = event.studentId == studentId && event.courseId == courseId }
      .conditionalEvolve(event.courseId == courseId) {
        apply { studentsInCourse = studentsInCourse + 1 }
      }
      .conditionalEvolve(event.studentId == studentId) {
        apply { coursesForStudent = coursesForStudent + 1 }
      }

}
