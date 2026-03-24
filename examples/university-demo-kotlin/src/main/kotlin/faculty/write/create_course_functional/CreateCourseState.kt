package org.axonframework.examples.university.faculty.write.create_course_functional

import org.axonframework.examples.university.faculty.FacultyTags.COURSE
import org.axonframework.examples.university.faculty.events.CourseCreated
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator

@EventSourcedEntity(tagKey = COURSE)
internal class CreateCourseState @EntityCreator constructor() {

    private var created: Boolean = false

    fun decide(command: CreateCourse): List<Any> {
        if (created) {
            return listOf()
        }
        return listOf(CourseCreated(command.courseId, command.name, command.capacity))
    }

    @EventSourcingHandler
    fun evolve(event: CourseCreated): CreateCourseState = apply {
        created = true
    }
}
