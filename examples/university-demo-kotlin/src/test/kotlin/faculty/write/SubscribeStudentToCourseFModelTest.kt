package org.axonframework.examples.university.faculty.write

import org.axonframework.examples.university.TestFixtures
import org.axonframework.examples.university.faculty.events.CourseCreated
import org.axonframework.examples.university.faculty.events.StudentEnrolledInFaculty
import org.axonframework.examples.university.faculty.events.StudentSubscribedToCourse
import org.axonframework.examples.university.faculty.write.create_course.CreateCourse
import org.axonframework.examples.university.faculty.write.create_course.registerCreateCourse
import org.axonframework.examples.university.faculty.write.enroll_student.EnrollStudent
import org.axonframework.examples.university.faculty.write.enroll_student.registerEnrollStudent
import org.axonframework.examples.university.faculty.write.subscribe_student_fmodel.SubscribeStudentToCourse
import org.axonframework.examples.university.faculty.write.subscribe_student_fmodel.registerSubscribeStudentToCourseFModel
import org.axonframework.examples.university.shared.ids.CourseId
import org.axonframework.examples.university.shared.ids.StudentId
import org.axonframework.extension.kotlin.test.exception
import org.axonframework.extension.kotlin.test.whenever
import org.axonframework.test.extension.AxonFrameworkExtension
import org.axonframework.test.extension.AxonTestFixtureProvider
import org.axonframework.test.extension.ProvidedAxonTestFixture
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AxonFrameworkExtension::class)
class SubscribeStudentToCourseFModelTest {

    @ProvidedAxonTestFixture
    fun fixture(): AxonTestFixtureProvider = TestFixtures.universityFixture {
        registerCreateCourse()
        registerEnrollStudent()
        registerSubscribeStudentToCourseFModel()
    }

    @Test
    fun `successfully subscribe student to course`(fixture: AxonTestFixture) {
        val courseId = CourseId.Companion.random()
        val studentId = StudentId.Companion.random()
        fixture
            .given()
            .event(CourseCreated(courseId, "Physics", 3))
            .event(StudentEnrolledInFaculty(studentId, "John", "Doe"))
            .whenever(SubscribeStudentToCourse(studentId, courseId))
            .then()
            .success()
            .events(StudentSubscribedToCourse(studentId, courseId))
    }

    @Test
    fun `fail to subscribe student to course if it is full`(fixture: AxonTestFixture) {
        val courseId = CourseId.random()
        val studentId = StudentId.random()
        val studentId2 = StudentId.random()
        fixture
            .given()
            .command(CreateCourse(courseId, "Physics", 1))
            .command(EnrollStudent(studentId, "John", "Doe"))
            .command(EnrollStudent(studentId2, "Kermit", "The Frog"))
            .command(SubscribeStudentToCourse(studentId, courseId))
            .whenever(SubscribeStudentToCourse(studentId2, courseId))
            .then()
            .exception(IllegalStateException::class)
    }

}
