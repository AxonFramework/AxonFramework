package io.axoniq.demo.university.faculty.write.subscribestudent;

import io.axoniq.demo.university.UniversityAxonApplication;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

class SubscribeStudentToCourseTest {

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
               .event(new StudentEnrolledInFaculty(studentId.raw(), "Mateusz", "Nowak"))
               .event(new CourseCreated(courseId.raw(), "Axon Framework 5: Be a PRO", 2))
               .when()
               .command(new SubscribeStudentToCourse(studentId, courseId))
               .then()
               .events(new StudentSubscribedToCourse(studentId.raw(), courseId.raw()));
    }

    @Test
    void studentAlreadySubscribed() {
        var courseId = CourseId.random();
        var studentId = StudentId.random();

        fixture.given()
               .event(new StudentEnrolledInFaculty(studentId.raw(), "Allard", "Buijze"))
               .event(new CourseCreated(courseId.raw(), "Axon Framework 5: Be a PRO", 2))
               .event(new StudentSubscribedToCourse(studentId.raw(), courseId.raw()))
               .when()
               .command(new SubscribeStudentToCourse(studentId, courseId))
               .then()
               .exception(RuntimeException.class, "Student already subscribed to this course");
    }

    @Test
    void studentAlreadySubscribedAnotherCourse() {
        var courseId = CourseId.random();
        var anotherCourseId = CourseId.random();
        var studentId = StudentId.random();

        fixture.given()
                .event(new StudentEnrolledInFaculty(studentId.raw(), "Allard", "Buijze"))
                .event(new CourseCreated(courseId.raw(), "Axon Framework 5: Be a PRO", 2))
                .event(new StudentSubscribedToCourse(studentId.raw(), anotherCourseId.raw()))
                .when()
                .command(new SubscribeStudentToCourse(studentId, courseId))
                .then()
                .events(new StudentSubscribedToCourse(studentId.raw(), courseId.raw()));
    }

    @Test
    void studentSubscribedIfAnotherOneAlreadySubscribed() {
        var courseId = CourseId.random();
        var studentId = StudentId.random();
        var anotherStudentId = StudentId.random();

        fixture.given()
                .event(new StudentEnrolledInFaculty(studentId.raw(), "Allard", "Buijze"))
                .event(new StudentEnrolledInFaculty(anotherStudentId.raw(), "Marc", "Gathier"))
                .event(new CourseCreated(courseId.raw(), "Axon Framework 5: Be a PRO", 2))
                .event(new StudentSubscribedToCourse(anotherStudentId.raw(), courseId.raw()))
                .when()
                .command(new SubscribeStudentToCourse(studentId, courseId))
                .then()
                .events(new StudentSubscribedToCourse(studentId.raw(), courseId.raw()));
    }

    @Test
    void courseFullyBooked() {
        var courseId = CourseId.random();
        var student1Id = StudentId.random();
        var student2Id = StudentId.random();
        var student3Id = StudentId.random();

        fixture.given()
               .event(new StudentEnrolledInFaculty(student1Id.raw(), "Mateusz", "Nowak"))
               .event(new StudentEnrolledInFaculty(student2Id.raw(), "Steven", "van Beelen"))
               .event(new StudentEnrolledInFaculty(student3Id.raw(), "Mitchell", "Herrijgers"))
               .event(new CourseCreated(courseId.raw(), "Event Sourcing Masterclass", 2))
               .event(new StudentSubscribedToCourse(student1Id.raw(), courseId.raw()))
               .event(new StudentSubscribedToCourse(student2Id.raw(), courseId.raw()))
               .when()
               .command(new SubscribeStudentToCourse(student3Id, courseId))
               .then()
               .exception(RuntimeException.class, "Course is fully booked");
    }

    @Test
    void studentSubscribedToTooManyCourses() {
        var studentId = StudentId.random();
        var course1Id = CourseId.random();
        var course2Id = CourseId.random();
        var course3Id = CourseId.random();
        var targetCourseId = CourseId.random();

        fixture.given()
                .event(new StudentEnrolledInFaculty(studentId.raw(), "Milan", "Savic"))
                .event(new CourseCreated(targetCourseId.raw(), "Programming", 10))
                .event(new CourseCreated(course1Id.raw(), "Course 1", 10))
                .event(new CourseCreated(course2Id.raw(), "Course 2", 10))
                .event(new CourseCreated(course3Id.raw(), "Course 3", 10))
                .event(new StudentSubscribedToCourse(studentId.raw(), course1Id.raw()))
                .event(new StudentSubscribedToCourse(studentId.raw(), course2Id.raw()))
                .event(new StudentSubscribedToCourse(studentId.raw(), course3Id.raw()))
                .when()
                .command(new io.axoniq.demo.university.faculty.write.subscribestudentmulti.SubscribeStudentToCourse(studentId, targetCourseId))
                .then()
                .noEvents()
                .exceptionSatisfies(thrown -> assertThat(thrown)
                        .isInstanceOf(RuntimeException.class)
                        .hasMessage("Student subscribed to too many courses")
                );
    }
}
