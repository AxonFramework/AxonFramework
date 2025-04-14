package io.axoniq.demo.university.faculty.events;

import io.axoniq.demo.university.faculty.FacultyTags;
import org.axonframework.eventsourcing.annotations.EventTag;

public record StudentUnsubscribedFromCourse(
        @EventTag(key = FacultyTags.STUDENT_ID)
        String studentId,
        @EventTag(key = FacultyTags.COURSE_ID)
        String courseId
) {

}
