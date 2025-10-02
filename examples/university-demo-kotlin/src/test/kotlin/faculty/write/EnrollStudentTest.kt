package io.axoniq.demo.university.faculty.write

import io.axoniq.demo.university.UniversityKotlinApplication
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty
import io.axoniq.demo.university.faculty.write.create_course.registerCreateCourse
import io.axoniq.demo.university.faculty.write.enroll_student.EnrollStudent
import io.axoniq.demo.university.faculty.write.enroll_student.registerEnrollStudent
import io.axoniq.demo.university.shared.ids.StudentId
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class EnrollStudentTest {

  private lateinit var fixture: AxonTestFixture

  /**
   * 	The @BeforeEach marks this method to be called before any test is executed in our test class.
   * 	Adding the code to create the AxonTestFixture here will ensure that we have a fresh fixture for
   * 	each test case, and thus we make our different tests independent.
   */
  @BeforeEach
  fun beforeEach() {
    fixture = AxonTestFixture.with(
      UniversityKotlinApplication.configurer().registerEnrollStudent(),
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
  fun `Given noEvents - When ErollStudent - Then StudenEnrolled`() {
    val studentId = StudentId.random()
    fixture
      .given()
      .noPriorActivity()
      .`when`()
      .command(EnrollStudent(studentId, "Kermit", "Frog"))
      .then()
      .events(StudentEnrolledInFaculty(studentId, "Kermit", "Frog"))
  }
}
