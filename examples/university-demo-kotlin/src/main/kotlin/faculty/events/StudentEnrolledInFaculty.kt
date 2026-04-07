package org.axonframework.examples.university.faculty.events

import org.axonframework.examples.university.faculty.FacultyTags
import org.axonframework.examples.university.shared.ids.StudentId
import org.axonframework.eventsourcing.annotation.EventTag

data class StudentEnrolledInFaculty(
    @EventTag(key = FacultyTags.STUDENT)
    val studentId: StudentId,
    val firstName: String,
    val lastName: String,
) : FacultyEvent
