package io.axoniq.demo.university.faculty.read.coursestats;

import io.axoniq.demo.university.shared.ids.CourseId;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class InMemoryCourseStatsRepository implements CourseStatsRepository {

    private final ConcurrentHashMap<CourseId, CoursesStatsReadModel> stats = new ConcurrentHashMap<>();

    @Override
    public CoursesStatsReadModel save(CoursesStatsReadModel stats) {
        this.stats.put(stats.courseId(), stats);
        return stats;
    }

    @Override
    public Optional<CoursesStatsReadModel> findById(CourseId courseId) {
        return Optional.ofNullable(stats.get(courseId));
    }

}
