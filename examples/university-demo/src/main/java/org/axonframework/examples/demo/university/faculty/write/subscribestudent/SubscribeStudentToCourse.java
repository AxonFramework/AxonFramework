package org.axonframework.examples.demo.university.faculty.write.subscribestudent;

import org.axonframework.examples.demo.university.shared.ids.CourseId;
import org.axonframework.examples.demo.university.shared.ids.StudentId;
import org.axonframework.modelling.annotation.TargetEntityId;

public record SubscribeStudentToCourse(StudentId studentId, CourseId courseId) {

    @TargetEntityId
    private SubscriptionId subscriptionId() {
        return new SubscriptionId(courseId, studentId);
    }
}