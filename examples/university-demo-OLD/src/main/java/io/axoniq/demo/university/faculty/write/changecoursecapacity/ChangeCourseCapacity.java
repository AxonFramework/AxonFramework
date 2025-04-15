package io.axoniq.demo.university.faculty.write.changecoursecapacity;

import io.axoniq.demo.university.shared.ids.CourseId;
import org.axonframework.modelling.annotation.TargetEntityId;

public record ChangeCourseCapacity(@TargetEntityId CourseId courseId, int capacity) {

}
