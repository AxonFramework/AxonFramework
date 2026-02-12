package org.axonframework.examples.demo.university.faculty.events;

import org.axonframework.examples.demo.university.faculty.FacultyTags;
import org.axonframework.examples.demo.university.shared.ids.CourseId;
import org.axonframework.examples.demo.university.shared.ids.FacultyId;
import org.axonframework.eventsourcing.annotation.EventTag;

public record CourseRenamed(
        @EventTag(key = FacultyTags.FACULTY_ID)
        FacultyId facultyId,
        @EventTag(key = FacultyTags.COURSE_ID)
        CourseId courseId,
        String name
) {

}
