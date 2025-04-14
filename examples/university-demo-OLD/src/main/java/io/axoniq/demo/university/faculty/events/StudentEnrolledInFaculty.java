package io.axoniq.demo.university.faculty.events;

import io.axoniq.demo.university.faculty.FacultyTags;
import org.axonframework.eventsourcing.annotations.EventTag;

public record StudentEnrolledInFaculty(
        @EventTag(key = FacultyTags.STUDENT_ID)
        String studentId,
        String firstName,
        String lastName
) {

}
