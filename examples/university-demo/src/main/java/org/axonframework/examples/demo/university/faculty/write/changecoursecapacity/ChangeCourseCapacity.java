package org.axonframework.examples.demo.university.faculty.write.changecoursecapacity;

import org.axonframework.examples.demo.university.shared.ids.CourseId;
import org.axonframework.modelling.annotation.TargetEntityId;

public record ChangeCourseCapacity(@TargetEntityId CourseId courseId, int capacity) {

}
