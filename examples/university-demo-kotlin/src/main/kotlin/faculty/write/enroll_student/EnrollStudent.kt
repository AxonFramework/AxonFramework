package org.axonframework.examples.university.faculty.write.enroll_student

import org.axonframework.examples.university.shared.ids.StudentId
import org.axonframework.modelling.annotation.TargetEntityId

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
