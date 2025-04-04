package io.axoniq.demo.university.faculty.write.createcourseplain;

import io.axoniq.demo.university.faculty.write.CourseId;

public record CreateCourse(CourseId courseId, String name, int capacity) {

}
