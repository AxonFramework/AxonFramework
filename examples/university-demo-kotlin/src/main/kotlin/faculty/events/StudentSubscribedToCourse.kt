package org.axonframework.examples.university.faculty.events

import org.axonframework.eventsourcing.annotation.EventTag
import org.axonframework.examples.university.faculty.FacultyTags
import org.axonframework.examples.university.shared.ids.CourseId
import org.axonframework.examples.university.shared.ids.StudentId

data class StudentSubscribedToCourse(
    @EventTag(key = FacultyTags.STUDENT)
    val studentId: StudentId,

    @EventTag(key = FacultyTags.COURSE)
    val courseId: CourseId,
) : FacultyEvent
