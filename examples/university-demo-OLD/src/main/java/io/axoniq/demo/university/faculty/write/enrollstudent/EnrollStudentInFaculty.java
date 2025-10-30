package io.axoniq.demo.university.faculty.write.enrollstudent;

import io.axoniq.demo.university.shared.ids.StudentId;

public record EnrollStudentInFaculty(StudentId studentId,
                                     String firstName,
                                     String lastName) {
}
