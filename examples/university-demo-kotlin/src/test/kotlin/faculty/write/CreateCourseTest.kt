package io.axoniq.demo.university.faculty.write

import io.axoniq.demo.university.UniversityKotlinApplication
import io.axoniq.demo.university.faculty.events.CourseCreated
import io.axoniq.demo.university.shared.ids.CourseId
import io.axoniq.demo.university.faculty.write.create_course.CreateCourse
import io.axoniq.demo.university.faculty.write.create_course.registerCreateCourse
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled("TODO: Fix")
class CreateCourseTest {

    private lateinit var fixture: AxonTestFixture

    /**
     * 	The @BeforeEach marks this method to be called before any test is executed in our test class.
     * 	Adding the code to create the AxonTestFixture here will ensure that we have a fresh fixture for
     * 	each test case, and thus we make our different tests independent.
     */
    @BeforeEach
    fun beforeEach() {
        fixture = AxonTestFixture.with(
            UniversityKotlinApplication.configurer().registerCreateCourse(),
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
    fun `given noPriorEvents - when createCourse - then courseCreated`() {
        val courseId = CourseId.random()
        val courseName = "Event Sourcing in Practice"
        val capacity = 3

        fixture.given()
            .noPriorActivity()
            .`when`()
            .command(CreateCourse(courseId, courseName, capacity))
            .then()
            .success()
            .events(CourseCreated(courseId, courseName, capacity))
    }

    @Test
    fun `given CourseCreated - When CreateCourse - Then SuccessNoEvents`() {
        val courseId = CourseId.random()
        val courseName = "Event Sourcing in Practice"
        val capacity = 3

        fixture.given()
            .event(CourseCreated(courseId, courseName, capacity))
            .`when`()
            .command(CreateCourse(courseId, courseName, capacity))
            .then()
            .success()
            .noEvents()
    }
}
