package io.axoniq.demo.university.faculty.write.renamecourse;

import io.axoniq.demo.university.faculty.write.CourseId;

public record RenameCourse(CourseId courseId, String name) {

}
