package io.axoniq.demo.university.faculty.write.subscribestudentmulti;

import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;

public record SubscribeStudentToCourse(StudentId studentId, CourseId courseId) {

}