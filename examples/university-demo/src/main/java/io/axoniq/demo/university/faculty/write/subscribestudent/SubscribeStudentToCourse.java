package io.axoniq.demo.university.faculty.write.subscribestudent;

import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.modelling.annotations.TargetEntityId;

public record SubscribeStudentToCourse(StudentId studentId, CourseId courseId) {

    @TargetEntityId
    private SubscriptionId subscriptionId() {
        return new SubscriptionId(courseId, studentId);
    }
}