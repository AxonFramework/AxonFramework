package org.axonframework.examples.university.faculty.write

import org.axonframework.examples.university.TestFixtures
import org.axonframework.examples.university.faculty.events.CourseCreated
import org.axonframework.examples.university.faculty.write.create_course.CreateCourse
import org.axonframework.examples.university.faculty.write.create_course.registerCreateCourse
import org.axonframework.examples.university.shared.ids.CourseId
import org.axonframework.extension.kotlin.test.whenever
import org.axonframework.test.extension.AxonFrameworkExtension
import org.axonframework.test.extension.AxonTestFixtureProvider
import org.axonframework.test.extension.ProvidedAxonTestFixture
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AxonFrameworkExtension::class)
class CreateCourseTest {

    @ProvidedAxonTestFixture
    fun fixture(): AxonTestFixtureProvider = TestFixtures.universityFixture {
        registerCreateCourse()
    }

    @Test
    fun `given noPriorEvents - when createCourse - then courseCreated`(fixture: AxonTestFixture) {
        val courseId = CourseId.random()
        val courseName = "Event Sourcing in Practice"
        val capacity = 3

        fixture.given()
            .noPriorActivity()
            .whenever(CreateCourse(courseId, courseName, capacity))
            .then()
            .success()
            .events(CourseCreated(courseId, courseName, capacity))
    }

    @Test
    fun `given CourseCreated - When CreateCourse - Then SuccessNoEvents`(fixture: AxonTestFixture) {
        val courseId = CourseId.random()
        val courseName = "Event Sourcing in Practice"
        val capacity = 3

        fixture.given()
            .event(CourseCreated(courseId, courseName, capacity))
            .whenever(CreateCourse(courseId, courseName, capacity))
            .then()
            .success()
            .noEvents()
    }
}
