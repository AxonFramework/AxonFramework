package org.axonframework.examples.university.event;

import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.examples.university.shared.FacultyTags;

public record CourseFullyBookedNotificationSent(
        @EventTag(key = FacultyTags.COURSE_ID)
        CourseId courseId
) {

}