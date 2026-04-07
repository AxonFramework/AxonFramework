package org.axonframework.examples.university.faculty.write.subscribe_student_fmodel

import org.axonframework.examples.university.shared.ids.CourseId
import org.axonframework.examples.university.shared.ids.StudentId
import org.axonframework.examples.university.shared.ids.SubscriptionId
import org.axonframework.messaging.commandhandling.annotation.Command
import org.axonframework.modelling.annotation.TargetEntityId

data class SubscribeStudentToCourse(
    val studentId: StudentId,
    val courseId: CourseId,
) {
    @TargetEntityId
    val subscriptionId: SubscriptionId = SubscriptionId(studentId, courseId)
}
