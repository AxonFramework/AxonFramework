package org.axonframework.examples.university.faculty.write

import org.axonframework.examples.university.UniversityKotlinApplication
import org.axonframework.examples.university.faculty.events.CourseCreated
import org.axonframework.examples.university.faculty.events.StudentEnrolledInFaculty
import org.axonframework.examples.university.faculty.events.StudentSubscribedToCourse
import org.axonframework.examples.university.faculty.write.create_course.CreateCourse
import org.axonframework.examples.university.faculty.write.create_course.registerCreateCourse
import org.axonframework.examples.university.faculty.write.enroll_student.EnrollStudent
import org.axonframework.examples.university.faculty.write.enroll_student.registerEnrollStudent
import org.axonframework.examples.university.faculty.write.subscribe_student.SubscribeStudentToCourse
import org.axonframework.examples.university.faculty.write.subscribe_student.registerSubscribeStudentToCourse
import org.axonframework.examples.university.faculty.write.subscribe_student_polymorph.registerSubscribeStudentToCoursePolymorph
import org.axonframework.examples.university.shared.ids.CourseId
import org.axonframework.examples.university.shared.ids.StudentId
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
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
        fixture
            .given()
            .command(CreateCourse(courseId, "Physics", 3))
            .command(EnrollStudent(studentId, "John", "Doe"))
            .`when`()
            .command(SubscribeStudentToCourse(studentId, courseId))
            .then()
            .success()
            .events(StudentSubscribedToCourse(studentId, courseId))
    }

    @Test
    fun `fail to subscribe student to course if it is full`() {
        val courseId = CourseId.random()
        val studentId = StudentId.random()
        val studentId2 = StudentId.random()
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
