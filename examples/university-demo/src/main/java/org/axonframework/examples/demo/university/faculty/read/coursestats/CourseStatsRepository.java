package org.axonframework.examples.demo.university.faculty.read.coursestats;

import org.axonframework.examples.demo.university.shared.ids.CourseId;

import java.util.Optional;

public interface CourseStatsRepository {
    CoursesStatsReadModel save(CoursesStatsReadModel stats);

    Optional<CoursesStatsReadModel> findById(CourseId courseId);

    default CoursesStatsReadModel findByIdOrThrow(CourseId courseId) {
        return findById(courseId).orElseThrow(() -> new RuntimeException("Course with id " + courseId + " does not exist!"));
    }

}
