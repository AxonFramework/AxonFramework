package org.axonframework.examples.university.faculty.events

import org.axonframework.examples.university.faculty.FacultyTags
import org.axonframework.examples.university.shared.ids.CourseId
import org.axonframework.eventsourcing.annotation.EventTag
import org.axonframework.messaging.eventhandling.annotation.Event

@Event
data class CourseRenamed(
    @EventTag(key = FacultyTags.COURSE)
    val courseId: CourseId,
    val name: String
) : FacultyEvent
