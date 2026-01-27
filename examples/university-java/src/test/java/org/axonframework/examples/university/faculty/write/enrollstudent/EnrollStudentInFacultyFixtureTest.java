package org.axonframework.examples.university.faculty.write.enrollstudent;

import org.axonframework.examples.university.faculty.FacultyAxonTestFixture;
import org.axonframework.examples.university.event.StudentEnrolledInFaculty;
import org.axonframework.examples.university.write.enrollstudent.EnrollStudentInFaculty;
import org.axonframework.examples.university.write.enrollstudent.EnrollStudentInFacultyConfiguration;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EnrollStudentInFacultyFixtureTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        fixture = FacultyAxonTestFixture.slice(EnrollStudentInFacultyConfiguration::configure);
    }

    @AfterEach
    public void afterEach() {
        fixture.stop();
    }

    @Test
    public void givenStudentNotEnrolledInFactulty_WhenEnrollStudentInFaculty_ThenStudentEnrolledInFaculty() {
        var studentId = "student-1";
        var firstName = "First Name";
        var lastName = "Last Name";

        fixture.given()
                .when()
                .command(new EnrollStudentInFaculty(studentId, firstName, lastName))
                .then()
                .success()
                .events(new StudentEnrolledInFaculty(studentId, firstName, lastName));
    }

    @Test
    public void givenStudentEnrolledInFactulty_WhenEnrollStudentInFaculty_ThenNothing() {
        var studentId = "student-1";
        var firstName = "First Name";
        var lastName = "Last Name";

        fixture.given()
                .events(new StudentEnrolledInFaculty(studentId, firstName, lastName))
                .when()
                .command(new EnrollStudentInFaculty(studentId, firstName, lastName))
                .then()
                .success()
                .noEvents();
    }
}
