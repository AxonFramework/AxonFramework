package org.axonframework.examples.university.faculty.write.create_course

import org.axonframework.examples.university.shared.ids.CourseId
import org.axonframework.messaging.commandhandling.annotation.Command

@Command
data class CreateCourse(
    val courseId: CourseId,
    val name: String,
    val capacity: Int
) {
    companion object {
        const val ID = "courseId"
    }
}
