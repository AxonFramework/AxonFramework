package org.axonframework.examples.demo.university.faculty.events;

import org.axonframework.examples.demo.university.faculty.FacultyTags;
import org.axonframework.examples.demo.university.shared.ids.FacultyId;
import org.axonframework.eventsourcing.annotation.EventTag;

public record AllCoursesFullyBookedNotificationSent(
        @EventTag(key = FacultyTags.FACULTY_ID)
        FacultyId facultyId
) {

}