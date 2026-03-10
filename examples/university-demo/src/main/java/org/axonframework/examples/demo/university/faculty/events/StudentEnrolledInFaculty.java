package org.axonframework.examples.demo.university.faculty.events;

import org.axonframework.examples.demo.university.faculty.FacultyTags;
import org.axonframework.examples.demo.university.shared.ids.FacultyId;
import org.axonframework.examples.demo.university.shared.ids.StudentId;
import org.axonframework.eventsourcing.annotation.EventTag;

public record StudentEnrolledInFaculty(
        @EventTag(key = FacultyTags.FACULTY_ID)
        FacultyId facultyId,
        @EventTag(key = FacultyTags.STUDENT_ID)
        StudentId studentId,
        String firstName,
        String lastName
) {

}
