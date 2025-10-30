package io.axoniq.demo.university.faculty.write.enrollstudent;

import io.axoniq.demo.university.faculty.FacultyAxonTestFixture;
import io.axoniq.demo.university.faculty.Ids;
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty;
import io.axoniq.demo.university.shared.ids.StudentId;
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
        var studentId = StudentId.random();
        var firstName = "First Name";
        var lastName = "Last Name";

        fixture.given()
                .when()
                .command(new EnrollStudentInFaculty(studentId, firstName, lastName))
                .then()
                .success()
                .events(new StudentEnrolledInFaculty(Ids.FACULTY_ID, studentId, firstName, lastName));
    }

    @Test
    public void givenStudentEnrolledInFactulty_WhenEnrollStudentInFaculty_ThenNothing() {
        var studentId = StudentId.random();
        var firstName = "First Name";
        var lastName = "Last Name";

        fixture.given()
                .events(new StudentEnrolledInFaculty(Ids.FACULTY_ID, studentId, firstName, lastName))
                .when()
                .command(new EnrollStudentInFaculty(studentId, firstName, lastName))
                .then()
                .success()
                .noEvents();
    }
}
