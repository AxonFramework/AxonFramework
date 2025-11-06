package io.axoniq.demo.university.faculty.events;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.shared.ids.FacultyId;
import org.axonframework.eventsourcing.annotation.EventTag;

public record AllCoursesFullyBookedNotificationSent(
        @EventTag(key = FacultyTags.FACULTY_ID)
        FacultyId facultyId
) {

}