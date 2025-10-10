package io.axoniq.demo.university.faculty.write.subscribe_student_fmodel

import io.axoniq.demo.university._ext.evolveIf
import io.axoniq.demo.university.faculty.events.CourseCreated
import io.axoniq.demo.university.faculty.events.FacultyEvent
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse
import io.axoniq.demo.university.shared.ids.CourseId
import io.axoniq.demo.university.shared.ids.StudentId


const val MAX_COURSES_PER_STUDENT = 3

internal sealed interface State {

  /**
   * By default, we reject the command. It has to be implemented in the subclass.
   */
  fun decide(cmd: SubscribeStudentToCourse): List<Any> {
    throw UnsupportedOperationException("Command dispatched to incorrect state. Current type is '${this::class.simpleName}'.")
  }

  fun evolve(event: FacultyEvent): State

  object InitialState : State {
    override fun evolve(event: FacultyEvent): State =
      when (event) {
        is CourseCreated -> CourseCreatedState(courseId = event.courseId, capacity = event.capacity)
        is StudentEnrolledInFaculty -> StudentEnrolledState(studentId = event.studentId)
        else -> this
      }
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

    override fun evolve(event: FacultyEvent): State = when (event) {
      is StudentEnrolledInFaculty -> SubscriptionState(
        courseState = this,
        studentState = StudentEnrolledState(studentId = event.studentId)
      )
      else -> this
    }
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


    override fun evolve(event: FacultyEvent): State =
      when (event) {
        is CourseCreated -> SubscriptionState(
          courseState = CourseCreatedState(
            courseId = event.courseId,
            capacity = event.capacity
          ),
          studentState = this
        )
        else -> this
      }
  }

  /**
   * Subscription (student and course).
   */
  data class SubscriptionState(
    val courseState: CourseCreatedState,
    val studentState: StudentEnrolledState,
    val alreadySubscribed: Boolean = false
  ) : State {

    fun checkAlreadySubscribed() {
      check(!alreadySubscribed) { "Student already subscribed to this course" }
    }

    override fun decide(cmd: SubscribeStudentToCourse): List<Any> {
      courseState.checkCourseHasMoreSpace()
      studentState.checkStudentCanSubscribeMoreCourses()
      checkAlreadySubscribed()
      return listOf(StudentSubscribedToCourse(cmd.studentId, cmd.courseId))
    }

    override fun evolve(event: FacultyEvent): State = when (event) {
      is StudentSubscribedToCourse -> {
        evolveIf(event.courseId == courseState.courseId) {
          copy(courseState = courseState.evolveStudentSubscribed())
        }
          .evolveIf(event.studentId == studentState.studentId) {
            copy(studentState = studentState.evolveSubscribedToCourse())
          }
          .evolveIf(event.studentId == studentState.studentId && event.courseId == courseState.courseId) {
            copy(alreadySubscribed = true)
          }
      }
      else -> this
    }
  }
}
