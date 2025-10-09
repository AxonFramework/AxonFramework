package io.axoniq.demo.university.faculty.write.subscribe_student_polymorph

import io.axoniq.demo.university.faculty.events.CourseCreated
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse
import io.axoniq.demo.university.shared.ids.CourseId
import io.axoniq.demo.university.shared.ids.StudentId
import io.axoniq.demo.university.shared.ids.SubscriptionId
import org.axonframework.eventsourcing.annotations.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotations.EventSourcedEntity
import org.axonframework.eventsourcing.annotations.EventSourcingHandler
import org.axonframework.eventsourcing.annotations.reflection.EntityCreator
import org.axonframework.eventstreaming.EventCriteria
import org.axonframework.messaging.ClassBasedMessageTypeResolver

@EventSourcedEntity(
  concreteTypes = [
    State.InitialState::class,
    State.CourseCreatedState::class,
    State.StudentEnrolledState::class,
    State.SubscriptionState::class
  ]
)
sealed interface State {
  companion object {

    const val MAX_COURSES_PER_STUDENT = 3
    val CLASS_MESSAGE_TYPE_RESOLVER = ClassBasedMessageTypeResolver()

    @JvmStatic
    @EventCriteriaBuilder
    fun resolveCriteria(id: SubscriptionId): EventCriteria = EventCriteria.either(
      EventCriteria
        .havingTags(id.courseIdTag())
        .andBeingOneOfTypes(
          CLASS_MESSAGE_TYPE_RESOLVER,
          CourseCreated::class.java,
          StudentSubscribedToCourse::class.java,
        ),
      EventCriteria
        .havingTags(id.studentTag())
        .andBeingOneOfTypes(
          CLASS_MESSAGE_TYPE_RESOLVER,
          StudentEnrolledInFaculty::class.java,
          StudentSubscribedToCourse::class.java,
        )
    )

    @JvmStatic
    @EntityCreator
    fun initialState(): State {
      return InitialState
    }
  }

  /**
   * By default, we reject the command. It has to be implemented in the subclass.
   */
  fun decide(cmd: SubscribeStudentToCourse): List<Any> {
    throw UnsupportedOperationException("Command dispatched to incorrect state. Current type is '${this::class.simpleName}'.")
  }

  /**
   * Conditional state evolver.
   * @param condition A condition if the state should be evolved.
   * @param stateEvolver  A state evolving function.
   * @return current or evolved stated, depending on the value of condition.
   */
  fun conditionalEvolve(condition: Boolean, stateEvolver: (State) -> State): State {
    return if (condition) {
      stateEvolver(this)
    } else {
      this
    }
  }

  object InitialState : State {

    @EventSourcingHandler
    fun evolve(event: CourseCreated): State = CourseCreatedState(
      courseId = event.courseId,
      capacity = event.capacity
    )

    @EventSourcingHandler
    fun evolve(event: StudentEnrolledInFaculty): State = StudentEnrolledState(
      studentId = event.studentId
    )
  }

  /**
   * Course but no student.
   */
  data class CourseCreatedState(
    val courseId: CourseId,
    val capacity: Int,
    val subscribedStudents: Int = 0
  ) : State {

    fun checkCourseHasMoreSpace() {
      check(capacity > subscribedStudents) { "Course is fully booked" }
    }

    fun evolveStudentSubscribed() = copy(subscribedStudents = subscribedStudents + 1)

    @EventSourcingHandler
    fun evolve(event: StudentEnrolledInFaculty): State = SubscriptionState(
      courseState = this,
      studentState = StudentEnrolledState(studentId = event.studentId)
    )
  }

  /**
   * Student but no course.
   */
  data class StudentEnrolledState(
    val studentId: StudentId,
    val coursesForStudent: Int = 0
  ) : State {

    fun checkStudentCanSubscribeMoreCourses() {
      check(this.coursesForStudent < MAX_COURSES_PER_STUDENT) { "Student subscribed to too many courses" }
    }

    fun evolveSubscribedToCourse(): StudentEnrolledState =
      copy(coursesForStudent = coursesForStudent + 1)

    @EventSourcingHandler
    fun evolve(event: CourseCreated): State = SubscriptionState(
      courseState = CourseCreatedState(
        courseId = event.courseId,
        capacity = event.capacity
      ),
      studentState = this
    )

  }

  /**
   * Subscription (student and course).
   */
  data class SubscriptionState(
    val courseState: CourseCreatedState,
    val studentState: StudentEnrolledState,
    val alreadySubscribed: Boolean = false
  ) : State {

    override fun decide(cmd: SubscribeStudentToCourse): List<Any> {
      courseState.checkCourseHasMoreSpace()
      studentState.checkStudentCanSubscribeMoreCourses()
      checkAlreadySubscribed()
      return listOf(StudentSubscribedToCourse(cmd.studentId, cmd.courseId))
    }

    fun checkAlreadySubscribed() {
      check(!alreadySubscribed) { "Student already subscribed to this course" }
    }

    @EventSourcingHandler
    fun evolve(event: StudentSubscribedToCourse): State =
      conditionalEvolve(event.courseId == courseState.courseId) {
        copy(courseState = courseState.evolveStudentSubscribed())
      }
        .conditionalEvolve(event.studentId == studentState.studentId) {
          copy(studentState = studentState.evolveSubscribedToCourse())
        }
        .conditionalEvolve(event.studentId == studentState.studentId && event.courseId == courseState.courseId) {
          copy(alreadySubscribed = true)
        }

  }
}
