package org.axonframework.examples.demo.university.faculty.write.renamecourse;

import org.axonframework.examples.demo.university.shared.ids.CourseId;
import org.axonframework.modelling.annotation.TargetEntityId;

public record RenameCourse(@TargetEntityId CourseId courseId, String name) {

}
