package org.axonframework.examples.university.shared.ids

import org.axonframework.examples.university.faculty.FacultyTags
import org.axonframework.messaging.eventstreaming.Tag

data class SubscriptionId(
    val studentId: StudentId,
    val courseId: CourseId,
) {
    fun studentTag() = Tag.of(FacultyTags.STUDENT, studentId.toString())
    fun courseIdTag() = Tag.of(FacultyTags.COURSE, courseId.toString())
}


