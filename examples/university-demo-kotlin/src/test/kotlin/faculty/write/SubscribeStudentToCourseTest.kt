package io.axoniq.demo.university.faculty.write

import io.axoniq.demo.university.UniversityKotlinApplication
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse
import io.axoniq.demo.university.faculty.write.create_course.CreateCourse
import io.axoniq.demo.university.faculty.write.create_course.registerCreateCourse
import io.axoniq.demo.university.faculty.write.enroll_student.EnrollStudent
import io.axoniq.demo.university.faculty.write.enroll_student.registerEnrollStudent
import io.axoniq.demo.university.faculty.write.subscribe_student.SubscribeStudentToCourse
import io.axoniq.demo.university.faculty.write.subscribe_student.registerSubscribeStudentToCourse
import io.axoniq.demo.university.shared.ids.CourseId
import io.axoniq.demo.university.shared.ids.StudentId
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscribeStudentToCourseTest {

  private lateinit var fixture: AxonTestFixture

  /**
   * 	The @BeforeEach marks this method to be called before any test is executed in our test class.
   * 	Adding the code to create the AxonTestFixture here will ensure that we have a fresh fixture for
   * 	each test case, and thus we make our different tests independent.
   */
  @BeforeEach
  fun beforeEach() {
    fixture = AxonTestFixture.with(
      UniversityKotlinApplication.configurer()
        .registerCreateCourse()
        .registerEnrollStudent()
        .registerSubscribeStudentToCourse(),
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
    val courseId = CourseId.random()
    val studentId = StudentId.random()
    fixture.given()
      .command(CreateCourse(courseId, "Physics", 3))
      .command(EnrollStudent(studentId, "John", "Doe"))
      .`when`()
      .command(SubscribeStudentToCourse(studentId, courseId))
      .then()
      .success()
      .events(StudentSubscribedToCourse(studentId, courseId))
  }
}
