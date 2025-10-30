package io.axoniq.demo.university.faculty.write.subscribestudentmulti;

import io.axoniq.demo.university.faculty.FacultyAxonTestFixture;
import io.axoniq.demo.university.faculty.Ids;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubscribeStudentToCourseAxonFixtureTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        fixture = FacultyAxonTestFixture.slice(SubscribeStudentMultiEntityConfiguration::configure);
    }

    @AfterEach
    void afterEach() {
        fixture.stop();
    }

    @Test
    void successfulSubscription() {
        var courseId = CourseId.random();
        var studentId = StudentId.random();

        fixture.given()
                .event(new StudentEnrolledInFaculty(Ids.FACULTY_ID, studentId, "Mateusz", "Nowak"))
                .event(new CourseCreated(Ids.FACULTY_ID, courseId, "Axon Framework 5: Be a PRO", 2))
                .when()
                .command(new SubscribeStudentToCourse(studentId, courseId))
                .then()
                .events(new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId, courseId));
    }

    @Test
    void studentAlreadySubscribed() {
        var courseId = CourseId.random();
        var studentId = StudentId.random();

        fixture.given()
                .event(new StudentEnrolledInFaculty(Ids.FACULTY_ID, studentId, "Allard", "Buijze"))
                .event(new CourseCreated(Ids.FACULTY_ID, courseId, "Axon Framework 5: Be a PRO", 2))
                .event(new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId, courseId))
                .when()
                .command(new SubscribeStudentToCourse(studentId, courseId))
                .then()
                .exceptionSatisfies(thrown -> assertThat(thrown)
                        .hasMessageContaining("Student already subscribed to this course")
                );
    }

    @Test
    void courseFullyBooked() {
        var courseId = CourseId.random();
        var student1Id = StudentId.random();
        var student2Id = StudentId.random();
        var student3Id = StudentId.random();

        fixture.given()
                .event(new StudentEnrolledInFaculty(Ids.FACULTY_ID, student1Id, "Mateusz", "Nowak"))
                .event(new StudentEnrolledInFaculty(Ids.FACULTY_ID, student2Id, "Steven", "van Beelen"))
                .event(new StudentEnrolledInFaculty(Ids.FACULTY_ID, student3Id, "Mitchell", "Herrijgers"))
                .event(new CourseCreated(Ids.FACULTY_ID, courseId, "Event Sourcing Masterclass", 2))
                .event(new StudentSubscribedToCourse(Ids.FACULTY_ID, student1Id, courseId))
                .event(new StudentSubscribedToCourse(Ids.FACULTY_ID, student2Id, courseId))
                .when()
                .command(new SubscribeStudentToCourse(student3Id, courseId))
                .then()
                .noEvents()
                .exceptionSatisfies(thrown -> assertThat(thrown)
                        .hasMessageContaining("Course is fully booked")
                );
    }

    @Test
    void studentSubscribedToTooManyCourses() {
        var studentId = StudentId.random();
        var course1Id = CourseId.random();
        var course2Id = CourseId.random();
        var course3Id = CourseId.random();
        var targetCourseId = CourseId.random();

        fixture.given()
                .event(new StudentEnrolledInFaculty(Ids.FACULTY_ID, studentId, "Milan", "Savic"))
                .event(new CourseCreated(Ids.FACULTY_ID, targetCourseId, "Programming", 10))
                .event(new CourseCreated(Ids.FACULTY_ID, course1Id, "Course 1", 10))
                .event(new CourseCreated(Ids.FACULTY_ID, course2Id, "Course 2", 10))
                .event(new CourseCreated(Ids.FACULTY_ID, course3Id, "Course 3", 10))
                .event(new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId, course1Id))
                .event(new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId, course2Id))
                .event(new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId, course3Id))
                .when()
                .command(new SubscribeStudentToCourse(studentId, targetCourseId))
                .then()
                .noEvents()
                .exceptionSatisfies(thrown -> assertThat(thrown)
                        .hasMessageContaining("Student subscribed to too many courses")
                );
    }
}
