package io.axoniq.demo.university.faculty.events;

import io.axoniq.demo.university.faculty.FacultyTags;
import org.axonframework.eventsourcing.annotations.EventTag;

public record CourseCapacityChanged(
        @EventTag(key = FacultyTags.COURSE_ID)
        String courseId,
        int capacity
) {

}