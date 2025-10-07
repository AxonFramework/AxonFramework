package io.axoniq.demo.university.faculty.write.enroll_student

import io.axoniq.demo.university.faculty.FacultyTags
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty
import org.axonframework.eventsourcing.annotations.EventSourcedEntity
import org.axonframework.eventsourcing.annotations.EventSourcingHandler
import org.axonframework.eventsourcing.annotations.reflection.EntityCreator

@EventSourcedEntity(tagKey = FacultyTags.STUDENT_ID)
class EnrollStudentState @EntityCreator constructor() {
  var enrolled = false

  fun decide(cmd: EnrollStudent): List<Any> = if (enrolled) {
    emptyList()
  } else {
    listOf(StudentEnrolledInFaculty(cmd.studentId, cmd.firstName, cmd.lastName))
  }

  @EventSourcingHandler
  fun evolve(evt: StudentEnrolledInFaculty) = apply {
    enrolled = true
  }
}
