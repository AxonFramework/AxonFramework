package io.axoniq.demo.university.faculty.write

import io.axoniq.demo.university.TestFixtures
import io.axoniq.demo.university.faculty.Faculty.Event.CourseCreated
import io.axoniq.demo.university.shared.CourseId
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.Test

class CreateCourseTest {

  private val fixture = AxonTestFixture.with(TestFixtures.APPLICATION_CONFIGURER, { it.disableAxonServer() })

  @Test
  fun `given noPriorEvents - when createCourse - then courseCreated`() {
    fixture.given()
      .noPriorActivity()
      .`when`()
      .command(CreateCourse(CourseId("4711"), "Physics I", 25))
      .then()
      .success()
      .events(CourseCreated(CourseId("4711"), "Physics I", 25))
  }
}
