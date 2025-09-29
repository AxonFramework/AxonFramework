package io.axoniq.demo.university.faculty.read.coursestats;

import org.axonframework.queryhandling.annotations.QueryHandler;

public record GetCourseStatsByIdQueryHandler(
        CourseStatsRepository repository
) {

    @QueryHandler
    GetCourseStatsById.Result handle(GetCourseStatsById query) {
        var stats = repository.findByIdOrThrow(query.courseId());
        return new GetCourseStatsById.Result(stats);
    }

}
