package org.axonframework.examples.demo.university.faculty.write.subscribestudentmulti;

import org.axonframework.examples.demo.university.shared.ids.CourseId;
import org.axonframework.examples.demo.university.shared.ids.StudentId;

public record SubscribeStudentToCourse(StudentId studentId, CourseId courseId) {

}