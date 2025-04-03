package io.axoniq.demo.university.faculty.write.subscribestudent;

import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;
import org.axonframework.modelling.command.annotation.TargetEntityId;

public record SubscribeStudent(StudentId studentId, CourseId courseId) {

    @TargetEntityId
    public SubscriptionId subscriptionId() {
        return new SubscriptionId(courseId, studentId);
    }
}