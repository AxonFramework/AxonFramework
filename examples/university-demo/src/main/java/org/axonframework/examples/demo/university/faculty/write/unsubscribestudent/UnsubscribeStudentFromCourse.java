package org.axonframework.examples.demo.university.faculty.write.unsubscribestudent;

import org.axonframework.examples.demo.university.shared.ids.CourseId;
import org.axonframework.examples.demo.university.shared.ids.StudentId;

public record UnsubscribeStudentFromCourse(StudentId studentId, CourseId courseId) {

}
