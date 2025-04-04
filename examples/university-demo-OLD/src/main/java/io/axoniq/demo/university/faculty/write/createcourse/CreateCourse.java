package io.axoniq.demo.university.faculty.write.createcourse;

import io.axoniq.demo.university.faculty.write.CourseId;
import org.axonframework.modelling.command.annotation.TargetEntityId;

public record CreateCourse(@TargetEntityId CourseId courseId, String name, int capacity) {

}
