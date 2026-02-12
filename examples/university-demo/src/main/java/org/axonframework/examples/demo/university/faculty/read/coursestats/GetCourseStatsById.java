package org.axonframework.examples.demo.university.faculty.read.coursestats;

import org.axonframework.examples.demo.university.shared.ids.CourseId;

public record GetCourseStatsById(CourseId courseId) {
    public record Result(CoursesStatsReadModel stats) {
    }
}
