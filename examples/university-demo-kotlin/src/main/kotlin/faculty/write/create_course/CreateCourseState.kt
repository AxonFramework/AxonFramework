package io.axoniq.demo.university.faculty.write.create_course

import io.axoniq.demo.university.faculty.FacultyTags.COURSE
import io.axoniq.demo.university.faculty.events.CourseCreated
import org.axonframework.eventsourcing.annotations.EventSourcedEntity
import org.axonframework.eventsourcing.annotations.EventSourcingHandler
import org.axonframework.eventsourcing.annotations.reflection.EntityCreator

@EventSourcedEntity(tagKey = COURSE)
class CreateCourseState @EntityCreator constructor() {

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
