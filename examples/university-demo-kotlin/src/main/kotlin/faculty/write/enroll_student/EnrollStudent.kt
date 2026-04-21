package org.axonframework.examples.university.faculty.write.enroll_student

import org.axonframework.examples.university.shared.ids.StudentId
import org.axonframework.messaging.commandhandling.annotation.Command
import org.axonframework.modelling.annotation.TargetEntityId

@Command
data class EnrollStudent(
    @TargetEntityId
    val studentId: StudentId,
    val firstName: String,
    val lastName: String,
) {
    companion object {
        const val ID_PROP = "studentId"
    }
}
