package io.axoniq.demo.university.faculty.write.subscribestudent;

import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;
import org.axonframework.modelling.annotation.TargetEntityId;

public record SubscribeStudent(StudentId studentId, CourseId courseId) {

    @TargetEntityId
    private SubscriptionId subscriptionId() {
        return new SubscriptionId(courseId, studentId);
    }
}