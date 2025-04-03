package io.axoniq.demo.university.faculty.events;

import io.axoniq.demo.university.faculty.FacultyTags;
import org.axonframework.eventsourcing.annotations.EventTag;

public record CourseRenamed(
        @EventTag(key = FacultyTags.COURSE_ID)
        String courseId,
        String name
) {

}
