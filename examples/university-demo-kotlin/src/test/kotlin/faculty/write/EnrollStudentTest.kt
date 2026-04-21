package org.axonframework.examples.university.faculty.write

import org.axonframework.examples.university.TestFixtures
import org.axonframework.examples.university.faculty.events.StudentEnrolledInFaculty
import org.axonframework.examples.university.faculty.write.enroll_student.EnrollStudent
import org.axonframework.examples.university.faculty.write.enroll_student.registerEnrollStudent
import org.axonframework.examples.university.shared.ids.StudentId
import org.axonframework.extension.kotlin.test.whenever
import org.axonframework.test.extension.AxonFrameworkExtension
import org.axonframework.test.extension.AxonTestFixtureProvider
import org.axonframework.test.extension.ProvidedAxonTestFixture
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AxonFrameworkExtension::class)
internal class EnrollStudentTest {

    @ProvidedAxonTestFixture
    fun fixture(): AxonTestFixtureProvider = TestFixtures.universityFixture {
        registerEnrollStudent()
    }

    @Test
    fun `Given noEvents - When ErollStudent - Then StudenEnrolled`(fixture: AxonTestFixture) {
        val studentId = StudentId.random()
        fixture
            .given()
            .noPriorActivity()
            .whenever(EnrollStudent(studentId, "Kermit", "Frog"))
            .then()
            .events(StudentEnrolledInFaculty(studentId, "Kermit", "Frog"))
    }
}
