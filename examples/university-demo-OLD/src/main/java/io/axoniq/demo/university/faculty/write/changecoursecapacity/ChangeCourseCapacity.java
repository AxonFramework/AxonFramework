package io.axoniq.demo.university.faculty.write.changecoursecapacity;

import io.axoniq.demo.university.shared.ids.CourseId;

public record ChangeCourseCapacity(CourseId courseId, int capacity) {

}
