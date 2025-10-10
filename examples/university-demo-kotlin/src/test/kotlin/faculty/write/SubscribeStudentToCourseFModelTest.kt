package io.axoniq.demo.university.faculty.write

import io.axoniq.demo.university.UniversityKotlinApplication
import io.axoniq.demo.university.faculty.events.CourseCreated
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse
import io.axoniq.demo.university.faculty.write.create_course.CreateCourse
import io.axoniq.demo.university.faculty.write.create_course.registerCreateCourse
import io.axoniq.demo.university.faculty.write.enroll_student.EnrollStudent
import io.axoniq.demo.university.faculty.write.enroll_student.registerEnrollStudent
import io.axoniq.demo.university.faculty.write.subscribe_student_fmodel.SubscribeStudentToCourse
import io.axoniq.demo.university.faculty.write.subscribe_student_fmodel.registerSubscribeStudentToCourseFModel
import io.axoniq.demo.university.shared.ids.CourseId
import io.axoniq.demo.university.shared.ids.StudentId
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscribeStudentToCourseFModelTest {

  private lateinit var fixture: AxonTestFixture

  /**
   * 	The @BeforeEach marks this method to be called before any test is executed in our test class.
   * 	Adding the code to create the AxonTestFixture here will ensure that we have a fresh fixture for
   * 	each test case, and thus we make our different tests independent.
   */
  @BeforeEach
  fun beforeEach() {
    fixture = AxonTestFixture.with(
      UniversityKotlinApplication.Companion.configurer()
        .registerCreateCourse()
        .registerEnrollStudent()
        .registerSubscribeStudentToCourseFModel(),
      { it.disableAxonServer() }
    )
  }

  /**
   * Remember after each test to stop the fixture, so all resources (like Axon Server connections) are properly released.
   */
  @AfterEach
  fun afterEach() {
    fixture.stop()
  }

  @Test
  fun `successfully subscribe student to course`() {
    val courseId = CourseId.Companion.random()
    val studentId = StudentId.Companion.random()
    fixture
      .given()
      .event(CourseCreated(courseId, "Physics", 3))
      .event(StudentEnrolledInFaculty(studentId, "John", "Doe"))
      .`when`()
      .command(SubscribeStudentToCourse(studentId, courseId))
      .then()
      .success()
      .events(StudentSubscribedToCourse(studentId, courseId))
  }

  @Test
  fun `fail to subscribe student to course if it is full`() {
    val courseId = CourseId.Companion.random()
    val studentId = StudentId.Companion.random()
    val studentId2 = StudentId.Companion.random()
    fixture
      .given()
      .command(CreateCourse(courseId, "Physics", 1))
      .command(EnrollStudent(studentId, "John", "Doe"))
      .command(EnrollStudent(studentId2, "Kermit", "The Frog"))
      .command(SubscribeStudentToCourse(studentId, courseId))
      .`when`()
      .command(SubscribeStudentToCourse(studentId2, courseId))
      .then()
      .exception(IllegalStateException::class.java)
  }

}
