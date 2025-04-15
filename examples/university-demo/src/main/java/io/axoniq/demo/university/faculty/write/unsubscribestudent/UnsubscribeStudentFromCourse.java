package io.axoniq.demo.university.faculty.write.unsubscribestudent;

import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;

public record UnsubscribeStudentFromCourse(StudentId studentId, CourseId courseId) {

}
