package io.axoniq.demo.university.faculty.write.subscribestudent;

import io.axoniq.demo.university.UniversityAxonApplication;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentEnrolledFaculty;
import io.axoniq.demo.university.faculty.events.StudentSubscribed;
import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

class SubscribeStudentTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        var application = new UniversityAxonApplication();
        fixture = AxonTestFixture.with(application.configurer());
    }

    @Test
    void successfulSubscription() {
        var courseId = CourseId.random();
        var studentId = StudentId.random();

        fixture.given()
               .event(new StudentEnrolledFaculty(studentId.raw(), "Mateusz", "Nowak"))
               .event(new CourseCreated(courseId.raw(), "Axon Framework 5: Be a PRO", 2))
               .when()
               .command(new SubscribeStudent(studentId, courseId))
               .then()
               .events(new StudentSubscribed(studentId.raw(), courseId.raw()));
    }

    @Test
    void studentAlreadySubscribed() {
        var courseId = CourseId.random();
        var studentId = StudentId.random();

        fixture.given()
               .event(new StudentEnrolledFaculty(studentId.raw(), "Allard", "Buijze"))
               .event(new CourseCreated(courseId.raw(), "Axon Framework 5: Be a PRO", 2))
               .event(new StudentSubscribed(studentId.raw(), courseId.raw()))
               .when()
               .command(new SubscribeStudent(studentId, courseId))
               .then()
               .exception(thrown -> assertThat(thrown)
                       .isInstanceOf(RuntimeException.class)
                       .hasMessage("Student already subscribed to this course")
               );
    }

    @Test
    void courseFullyBooked() {
        var courseId = CourseId.random();
        var student1Id = StudentId.random();
        var student2Id = StudentId.random();
        var student3Id = StudentId.random();

        fixture.given()
               .event(new StudentEnrolledFaculty(student1Id.raw(), "Milan", "Savic"))
               .event(new StudentEnrolledFaculty(student2Id.raw(), "Steven", "van Beelen"))
               .event(new StudentEnrolledFaculty(student3Id.raw(), "Mitchell", "Herrijgers"))
               .event(new CourseCreated(courseId.raw(), "Event Sourcing Masterclass", 2))
               .event(new StudentSubscribed(student1Id.raw(), courseId.raw()))
               .event(new StudentSubscribed(student2Id.raw(), courseId.raw()))
               .when()
               .command(new SubscribeStudent(student3Id, courseId))
               .then()
               .exception(thrown -> assertThat(thrown)
                       .isInstanceOf(RuntimeException.class)
                       .hasMessage("Course is fully booked")
               );
    }
}
