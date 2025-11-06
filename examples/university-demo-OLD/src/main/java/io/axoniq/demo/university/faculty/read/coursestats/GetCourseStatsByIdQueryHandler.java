package io.axoniq.demo.university.faculty.read.coursestats;

import org.axonframework.messaging.queryhandling.annotation.QueryHandler;

public record GetCourseStatsByIdQueryHandler(
        CourseStatsRepository repository
) {

    @QueryHandler
    GetCourseStatsById.Result handle(GetCourseStatsById query) {
        return repository.findById(query.courseId())
                .map(GetCourseStatsById.Result::new)
                .orElseGet(() -> new GetCourseStatsById.Result(null));
    }

}
