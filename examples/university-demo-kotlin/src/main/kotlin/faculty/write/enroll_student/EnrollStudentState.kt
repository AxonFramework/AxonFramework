package org.axonframework.examples.university.faculty.write.enroll_student

import org.axonframework.examples.university.faculty.FacultyTags
import org.axonframework.examples.university.faculty.events.StudentEnrolledInFaculty
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator

@EventSourcedEntity(tagKey = FacultyTags.STUDENT)
internal class EnrollStudentState @EntityCreator constructor() {
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
