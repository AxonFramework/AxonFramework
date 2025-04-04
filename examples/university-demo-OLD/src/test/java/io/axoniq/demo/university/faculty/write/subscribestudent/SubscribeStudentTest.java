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
               .exception(RuntimeException.class, "Student already subscribed to this course");
    }

    @Test
    void courseFullyBooked() {
        var courseId = CourseId.random();
        var student1Id = StudentId.random();
        var student2Id = StudentId.random();
        var student3Id = StudentId.random();

        fixture.given()
               .event(new StudentEnrolledFaculty(student1Id.raw(), "Mateusz", "Nowak"))
               .event(new StudentEnrolledFaculty(student2Id.raw(), "Steven", "van Beelen"))
               .event(new StudentEnrolledFaculty(student3Id.raw(), "Mitchell", "Herrijgers"))
               .event(new CourseCreated(courseId.raw(), "Event Sourcing Masterclass", 2))
               .event(new StudentSubscribed(student1Id.raw(), courseId.raw()))
               .event(new StudentSubscribed(student2Id.raw(), courseId.raw()))
               .when()
               .command(new SubscribeStudent(student3Id, courseId))
               .then()
               .exception(RuntimeException.class, "Course is fully booked");
    }

    @Test
    void studentSubscribedToTooManyCourses() {
        var studentId = StudentId.random();
        var course1Id = CourseId.random();
        var course2Id = CourseId.random();
        var course3Id = CourseId.random();
        var course4Id = CourseId.random();
        var course5Id = CourseId.random();
        var course6Id = CourseId.random();
        var course7Id = CourseId.random();
        var course8Id = CourseId.random();
        var course9Id = CourseId.random();
        var course10Id = CourseId.random();
        var targetCourseId = CourseId.random();

        fixture.given()
               .event(new StudentEnrolledFaculty(studentId.raw(), "Milan", "Savic"))
               .event(new CourseCreated(targetCourseId.raw(), "Programming", 10))
               .event(new CourseCreated(course1Id.raw(), "Course 1", 10))
               .event(new CourseCreated(course2Id.raw(), "Course 2", 10))
               .event(new CourseCreated(course3Id.raw(), "Course 3", 10))
               .event(new CourseCreated(course4Id.raw(), "Course 4", 10))
               .event(new CourseCreated(course5Id.raw(), "Course 5", 10))
               .event(new CourseCreated(course6Id.raw(), "Course 6", 10))
               .event(new CourseCreated(course7Id.raw(), "Course 7", 10))
               .event(new CourseCreated(course8Id.raw(), "Course 8", 10))
               .event(new CourseCreated(course9Id.raw(), "Course 9", 10))
               .event(new CourseCreated(course10Id.raw(), "Course 10", 10))
               .event(new StudentSubscribed(studentId.raw(), course1Id.raw()))
               .event(new StudentSubscribed(studentId.raw(), course2Id.raw()))
               .event(new StudentSubscribed(studentId.raw(), course3Id.raw()))
               .event(new StudentSubscribed(studentId.raw(), course4Id.raw()))
               .event(new StudentSubscribed(studentId.raw(), course5Id.raw()))
               .event(new StudentSubscribed(studentId.raw(), course6Id.raw()))
               .event(new StudentSubscribed(studentId.raw(), course7Id.raw()))
               .event(new StudentSubscribed(studentId.raw(), course8Id.raw()))
               .event(new StudentSubscribed(studentId.raw(), course9Id.raw()))
               .event(new StudentSubscribed(studentId.raw(), course10Id.raw()))
               .when()
               .command(new SubscribeStudent(studentId, targetCourseId))
               .then()
               .noEvents()
               .exceptionSatisfies(thrown -> assertThat(thrown)
                       .isInstanceOf(RuntimeException.class)
                       .hasMessage("Student subscribed to too many courses")
               );
    }
}
