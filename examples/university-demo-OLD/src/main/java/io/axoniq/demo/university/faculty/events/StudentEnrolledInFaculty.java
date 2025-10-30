package io.axoniq.demo.university.faculty.events;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.shared.ids.FacultyId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.eventsourcing.annotations.EventTag;

public record StudentEnrolledInFaculty(
        @EventTag(key = FacultyTags.FACULTY_ID)
        FacultyId facultyId,
        @EventTag(key = FacultyTags.STUDENT_ID)
        StudentId studentId,
        String firstName,
        String lastName
) {

}
