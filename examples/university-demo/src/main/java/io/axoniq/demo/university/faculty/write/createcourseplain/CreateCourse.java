package io.axoniq.demo.university.faculty.write.createcourseplain;

import io.axoniq.demo.university.shared.ids.CourseId;

public record CreateCourse(CourseId courseId, String name, int capacity) {

}
