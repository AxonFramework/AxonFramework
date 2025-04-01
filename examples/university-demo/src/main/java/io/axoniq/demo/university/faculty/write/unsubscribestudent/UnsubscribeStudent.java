package io.axoniq.demo.university.faculty.write.unsubscribestudent;

import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;

public record UnsubscribeStudent(StudentId studentId, CourseId courseId) {

}
