package org.axonframework.examples.demo.university.faculty.write.enrollstudent;

import org.axonframework.examples.demo.university.shared.ids.StudentId;

public record EnrollStudentInFaculty(StudentId studentId,
                                     String firstName,
                                     String lastName) {
}
