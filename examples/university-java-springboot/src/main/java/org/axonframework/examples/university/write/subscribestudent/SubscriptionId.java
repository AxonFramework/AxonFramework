package org.axonframework.examples.university.write.subscribestudent;

import org.axonframework.examples.university.shared.CourseId;

record SubscriptionId(CourseId courseId, String studentId) {

}
