package io.axoniq.demo.university.faculty.write.subscribestudentmulti;

import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;

public record SubscribeStudentToCourse(StudentId studentId, CourseId courseId) {

}