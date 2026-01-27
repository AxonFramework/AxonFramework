package org.axonframework.examples.university.faculty.write.subscribestudentmulti;

import org.axonframework.examples.university.faculty.FacultyAxonTestFixture;
import org.axonframework.examples.university.event.CourseCreated;
import org.axonframework.examples.university.event.StudentEnrolledInFaculty;
import org.axonframework.examples.university.event.StudentSubscribedToCourse;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.examples.university.write.subscribestudent.SubscribeStudentConfiguration;
import org.axonframework.examples.university.write.subscribestudent.SubscribeStudentToCourse;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubscribeStudentToCourseAxonFixtureTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        fixture = FacultyAxonTestFixture.slice(SubscribeStudentConfiguration::configure);
    }

    @AfterEach
    void afterEach() {
        fixture.stop();
    }

    @Test
    void successfulSubscription() {
        var courseId = CourseId.random();
        var studentId = "student-" + java.util.UUID.randomUUID().toString();

        fixture.given()
                .event(new StudentEnrolledInFaculty(studentId, "Mateusz", "Nowak"))
                .event(new CourseCreated(courseId, "Axon Framework 5: Be a PRO", 2))
                .when()
                .command(new SubscribeStudentToCourse(studentId, courseId))
                .then()
                .events(new StudentSubscribedToCourse(studentId, courseId));
    }

    @Test
    void studentAlreadySubscribed() {
        var courseId = CourseId.random();
        var studentId = "student-" + java.util.UUID.randomUUID().toString();

        fixture.given()
                .event(new StudentEnrolledInFaculty(studentId, "Allard", "Buijze"))
                .event(new CourseCreated(courseId, "Axon Framework 5: Be a PRO", 2))
                .event(new StudentSubscribedToCourse(studentId, courseId))
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
        var student1Id = "student-" + java.util.UUID.randomUUID().toString();
        var student2Id = "student-" + java.util.UUID.randomUUID().toString();
        var student3Id = "student-" + java.util.UUID.randomUUID().toString();

        fixture.given()
                .event(new StudentEnrolledInFaculty(student1Id, "Mateusz", "Nowak"))
                .event(new StudentEnrolledInFaculty(student2Id, "Steven", "van Beelen"))
                .event(new StudentEnrolledInFaculty(student3Id, "Mitchell", "Herrijgers"))
                .event(new CourseCreated(courseId, "Event Sourcing Masterclass", 2))
                .event(new StudentSubscribedToCourse(student1Id, courseId))
                .event(new StudentSubscribedToCourse(student2Id, courseId))
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
        var studentId = "student-" + java.util.UUID.randomUUID().toString();
        var course1Id = CourseId.random();
        var course2Id = CourseId.random();
        var course3Id = CourseId.random();
        var targetCourseId = CourseId.random();

        fixture.given()
                .event(new StudentEnrolledInFaculty(studentId, "Milan", "Savic"))
                .event(new CourseCreated(targetCourseId, "Programming", 10))
                .event(new CourseCreated(course1Id, "Course 1", 10))
                .event(new CourseCreated(course2Id, "Course 2", 10))
                .event(new CourseCreated(course3Id, "Course 3", 10))
                .event(new StudentSubscribedToCourse(studentId, course1Id))
                .event(new StudentSubscribedToCourse(studentId, course2Id))
                .event(new StudentSubscribedToCourse(studentId, course3Id))
                .when()
                .command(new SubscribeStudentToCourse(studentId, targetCourseId))
                .then()
                .noEvents()
                .exceptionSatisfies(thrown -> assertThat(thrown)
                        .hasMessageContaining("Student subscribed to too many courses")
                );
    }
}
