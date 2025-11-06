package io.axoniq.demo.university.faculty.write.subscribe_student_polymorph

import io.axoniq.demo.university.shared.ids.CourseId
import io.axoniq.demo.university.shared.ids.StudentId
import io.axoniq.demo.university.shared.ids.SubscriptionId
import org.axonframework.messaging.commandhandling.annotation.Command
import org.axonframework.modelling.annotation.TargetEntityId

data class SubscribeStudentToCourse(
  val studentId: StudentId,
  val courseId: CourseId,
) {
  @TargetEntityId
  val subscriptionId: SubscriptionId = SubscriptionId(studentId, courseId)
}
