package io.axoniq.demo.university.faculty.events;

import io.axoniq.demo.university.faculty.FacultyTags;
import org.axonframework.eventsourcing.annotations.EventTag;

public record AllCoursesFullyBookedNotificationSent(
        @EventTag(key = FacultyTags.FACULTY_ID)
        String facultyId
) {

}