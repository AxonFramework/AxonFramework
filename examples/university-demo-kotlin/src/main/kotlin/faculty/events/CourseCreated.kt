package org.axonframework.examples.university.faculty.events

import org.axonframework.eventsourcing.annotation.EventTag
import org.axonframework.examples.university.faculty.FacultyTags
import org.axonframework.examples.university.shared.ids.CourseId

data class CourseCreated(
    @EventTag(key = FacultyTags.COURSE)
    val courseId: CourseId,
    val name: String,
    val capacity: Int
) : FacultyEvent
