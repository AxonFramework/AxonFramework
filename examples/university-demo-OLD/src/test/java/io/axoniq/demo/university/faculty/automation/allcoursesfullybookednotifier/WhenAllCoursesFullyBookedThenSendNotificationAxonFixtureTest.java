package io.axoniq.demo.university.faculty.automation.allcoursesfullybookednotifier;

import io.axoniq.demo.university.faculty.FacultyAxonTestFixture;
import io.axoniq.demo.university.faculty.Ids;
import io.axoniq.demo.university.faculty.events.CourseCapacityChanged;
import io.axoniq.demo.university.shared.application.notifier.NotificationService;
import io.axoniq.demo.university.shared.configuration.NotificationServiceConfiguration;
import io.axoniq.demo.university.shared.infrastructure.notifier.RecordingNotificationService;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class WhenAllCoursesFullyBookedThenSendNotificationAxonFixtureTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        fixture = FacultyAxonTestFixture.slice(configurer -> {
            configurer = NotificationServiceConfiguration.configure(configurer);
            return AllCoursesFullyBookedNotifierConfiguration.configure(configurer);
        });
    }

    @AfterEach
    void afterEach() {
        fixture.stop();
    }

    @Test
    void givenCoursesFullyBookedInFaculty_ThenNotificationSent() {
        var studentId1 = StudentId.random();
        var studentId2 = StudentId.random();
        var courseId1 = CourseId.random();
        var courseId2 = CourseId.random();

        var expectedNotification = new NotificationService.Notification("admin", "All courses are fully booked now.");

        fixture.given()
                .events(
                        new CourseCreated(Ids.FACULTY_ID, courseId1, "Course 1", 2), // Create course with capacity 2
                        new CourseCreated(Ids.FACULTY_ID, courseId2, "Course 2", 2), // Create course with capacity 2
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId1, courseId1), // Fill first course
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId2, courseId1),
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId1, courseId2), // Fill second course
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId2, courseId2)  // This should trigger notification
                )
                .then()
                .await(r -> r.expect(cfg -> assertNotificationSent(cfg, expectedNotification, 1)));
    }

    private void assertNotificationSent(Configuration configuration, NotificationService.Notification expectedNotification, int times) {
        var notificationService = (RecordingNotificationService) configuration.getComponent(NotificationService.class);
        assertThat(notificationService.sent()).contains(expectedNotification);
        assertThat(notificationService.sent()).hasSize(times);
    }

    @Test
    void givenCoursesFullyBookedAndRemainSoOnSubsequentEvents_ThenDoNotSentNotificationTwice() {
        var studentId1 = StudentId.random();
        var studentId2 = StudentId.random();
        var courseId1 = CourseId.random();
        var courseId2 = CourseId.random();

        var expectedNotification = new NotificationService.Notification("admin", "All courses are fully booked now.");

        fixture.given()
                .events(
                        new CourseCreated(Ids.FACULTY_ID, courseId1, "Course 1", 2), // Create course with capacity 2
                        new CourseCreated(Ids.FACULTY_ID, courseId2, "Course 2", 2), // Create course with capacity 2
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId1, courseId1), // Fill first course
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId2, courseId1),
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId1, courseId2), // Fill second course
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId2, courseId2)  // This should trigger notification
                )
                .then()
                .await(r -> r.expect(cfg -> assertNotificationSent(cfg, expectedNotification, 1)))
                .and()
                .given()
                .events(
                        // Courses remain fully booked, so this should not clear the notified state, and thus not result in a new notification
                        new CourseCapacityChanged(Ids.FACULTY_ID, courseId2, 1)
                )
                .then()
                .await(r -> r.expect(cfg -> assertNotificationSent(cfg, expectedNotification, 1)));
    }


    @Test
    void givenCoursesFullyBookedInFacultyTwice_ThenSendNotificationTwice() {
        var studentId1 = StudentId.random();
        var studentId2 = StudentId.random();
        var studentId3 = StudentId.random();
        var courseId1 = CourseId.random();
        var courseId2 = CourseId.random();

        var expectedNotification = new NotificationService.Notification("admin", "All courses are fully booked now.");

        fixture.given()
                .events(
                        new CourseCreated(Ids.FACULTY_ID, courseId1, "Course 1", 2), // Create course with capacity 2
                        new CourseCreated(Ids.FACULTY_ID, courseId2, "Course 2", 2), // Create course with capacity 2
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId1, courseId1), // Fill first course
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId2, courseId1),
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId1, courseId2), // Fill second course
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId2, courseId2)  // This should trigger notification
                )
                .then()
                .await(r -> r.expect(cfg -> assertNotificationSent(cfg, expectedNotification, 1)))
                .and()
                .given()
                .events(
                        new CourseCapacityChanged(Ids.FACULTY_ID, courseId2, 3), // This should clear the notified state
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId3, courseId2)  // This should trigger notification
                )
                .then()
                .await(r -> r.expect(cfg -> assertNotificationSent(cfg, expectedNotification, 2)));
    }

    @Test
    void givenCoursesNotFullyBookedInFaculty_ThenNoNotificationSent() {
        var studentId1 = StudentId.random();
        var studentId2 = StudentId.random();
        var courseId1 = CourseId.random();
        var courseId2 = CourseId.random();

        fixture.given()
                .events(
                        new CourseCreated(Ids.FACULTY_ID, courseId1, "Course 1", 2), // Create course with capacity 2
                        new CourseCreated(Ids.FACULTY_ID, courseId2, "Course 2", 2), // Create course with capacity 2
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId1, courseId1), // Fill first course
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId2, courseId1),
                        new StudentSubscribedToCourse(Ids.FACULTY_ID, studentId1, courseId2)
                        // Second course not fully booked, so no notification expected
                )
                .then()
                .expect(this::assertNoNotificationSent);
    }

    private void assertNoNotificationSent(Configuration configuration) {
        await().during(1, TimeUnit.SECONDS)
                .until(() -> {
                    var notificationService = (RecordingNotificationService) configuration.getComponent(NotificationService.class);
                    return notificationService.sent().isEmpty();
                });
    }

}
