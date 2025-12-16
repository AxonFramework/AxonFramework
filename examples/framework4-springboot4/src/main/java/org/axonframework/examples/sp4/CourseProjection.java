package org.axonframework.examples.sp4;

import org.axonframework.eventhandling.EventHandler;
import org.axonframework.examples.sp4.event.CourseCreated;
import org.axonframework.examples.sp4.query.CourseSummary;
import org.axonframework.examples.sp4.query.FindAllCourses;
import org.axonframework.examples.sp4.query.FindCourseById;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory projection that keeps track of created courses.
 */
@Component
public class CourseProjection {

    private final Map<String, CourseSummary> courses = new ConcurrentHashMap<>();

    @EventHandler
    public void on(CourseCreated event) {
        courses.put(event.id(), new CourseSummary(event.id(), event.name()));
    }

    @QueryHandler
    public Collection<CourseSummary> handle(FindAllCourses query) {
        return courses.values();
    }

    @QueryHandler
    public Optional<CourseSummary> handle(FindCourseById query) {
        return Optional.ofNullable(courses.get(query.id()));
    }
}
