package org.axonframework.examples.demo.university.faculty.write.createcourse;

import org.axonframework.examples.demo.university.shared.ids.CourseId;

public record CreateCourse(CourseId courseId, String name, int capacity) {

}
