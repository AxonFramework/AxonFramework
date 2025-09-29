package io.axoniq.demo.university.faculty.read.coursestats;

import io.axoniq.demo.university.shared.ids.CourseId;

public record GetCourseStatsById(CourseId courseId) {
    public record Result(CoursesStatsReadModel stats) {
    }
}
