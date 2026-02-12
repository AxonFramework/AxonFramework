package org.axonframework.examples.demo.university.faculty.write.subscribestudent;

import org.axonframework.examples.demo.university.shared.ids.CourseId;
import org.axonframework.examples.demo.university.shared.ids.StudentId;

record SubscriptionId(CourseId courseId, StudentId studentId) {

}
