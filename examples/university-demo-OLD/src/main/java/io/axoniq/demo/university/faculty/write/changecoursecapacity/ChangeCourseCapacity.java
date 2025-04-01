package io.axoniq.demo.university.faculty.write.changecoursecapacity;

import io.axoniq.demo.university.faculty.write.CourseId;

public record ChangeCourseCapacity(CourseId courseId, int capacity) {

}
