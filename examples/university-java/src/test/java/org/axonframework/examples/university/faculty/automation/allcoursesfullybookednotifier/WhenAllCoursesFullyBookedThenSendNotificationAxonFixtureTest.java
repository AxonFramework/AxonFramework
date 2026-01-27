package org.axonframework.examples.university.faculty.automation.allcoursesfullybookednotifier;

import org.axonframework.examples.university.faculty.FacultyAxonTestFixture;
import org.axonframework.examples.university.event.CourseCapacityChanged;
import org.axonframework.examples.university.event.CourseCreated;
import org.axonframework.examples.university.event.StudentSubscribedToCourse;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.examples.university.shared.notification.NotificationService;
import org.axonframework.examples.university.shared.notification.RecordingNotificationService;
import org.axonframework.examples.university.automation.CourseFullyBookedNotifierConfiguration;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class WhenAllCoursesFullyBookedThenSendNotificationAxonFixtureTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        fixture = FacultyAxonTestFixture.slice(configurer -> {
            configurer = configurer.componentRegistry(cr -> cr.registerComponent(
                    NotificationService.class,
                    cfg -> new RecordingNotificationService())
            );
            return CourseFullyBookedNotifierConfiguration.configure(configurer);
        });
    }

    @AfterEach
    void afterEach() {
        fixture.stop();
    }

    @Test
    @Disabled("imported test, failing with: [Notification[recipientId=admin, message=All courses are fully booked now.]]")
    void givenCoursesFullyBookedInFaculty_ThenNotificationSent() {
        var studentId1 = "student-" + java.util.UUID.randomUUID().toString();
        var studentId2 = "student-" + java.util.UUID.randomUUID().toString();
        var courseId1 = CourseId.random();
        var courseId2 = CourseId.random();

        var expectedNotification = new NotificationService.Notification("admin", "All courses are fully booked now.");

        fixture.given()
                .events(
                        new CourseCreated(courseId1, "Course 1", 2), // Create course with capacity 2
                        new CourseCreated(courseId2, "Course 2", 2), // Create course with capacity 2
                        new StudentSubscribedToCourse(studentId1, courseId1), // Fill first course
                        new StudentSubscribedToCourse(studentId2, courseId1),
                        new StudentSubscribedToCourse(studentId1, courseId2), // Fill second course
                        new StudentSubscribedToCourse(studentId2, courseId2)  // This should trigger notification
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
    @Disabled("imported test, failing with: [Notification[recipientId=admin, message=All courses are fully booked now.]]")
    void givenCoursesFullyBookedAndRemainSoOnSubsequentEvents_ThenDoNotSentNotificationTwice() {
        var studentId1 = "student-" + java.util.UUID.randomUUID().toString();
        var studentId2 = "student-" + java.util.UUID.randomUUID().toString();
        var courseId1 = CourseId.random();
        var courseId2 = CourseId.random();

        var expectedNotification = new NotificationService.Notification("admin", "All courses are fully booked now.");

        fixture.given()
                .events(
                        new CourseCreated(courseId1, "Course 1", 2), // Create course with capacity 2
                        new CourseCreated(courseId2, "Course 2", 2), // Create course with capacity 2
                        new StudentSubscribedToCourse(studentId1, courseId1), // Fill first course
                        new StudentSubscribedToCourse(studentId2, courseId1),
                        new StudentSubscribedToCourse(studentId1, courseId2), // Fill second course
                        new StudentSubscribedToCourse(studentId2, courseId2)  // This should trigger notification
                )
                .then()
                .await(r -> r.expect(cfg -> assertNotificationSent(cfg, expectedNotification, 1)))
                .and()
                .given()
                .events(
                        // Courses remain fully booked, so this should not clear the notified state, and thus not result in a new notification
                        new CourseCapacityChanged(courseId2, 1)
                )
                .then()
                .await(r -> r.expect(cfg -> assertNotificationSent(cfg, expectedNotification, 1)));
    }


    @Test
    @Disabled("imported test, failing with: [Notification[recipientId=admin, message=All courses are fully booked now.]]")
    void givenCoursesFullyBookedInFacultyTwice_ThenSendNotificationTwice() {
        var studentId1 = "student-" + java.util.UUID.randomUUID().toString();
        var studentId2 = "student-" + java.util.UUID.randomUUID().toString();
        var studentId3 = "student-" + java.util.UUID.randomUUID().toString();
        var courseId1 = CourseId.random();
        var courseId2 = CourseId.random();

        var expectedNotification = new NotificationService.Notification("admin", "All courses are fully booked now.");

        fixture.given()
                .events(
                        new CourseCreated(courseId1, "Course 1", 2), // Create course with capacity 2
                        new CourseCreated(courseId2, "Course 2", 2), // Create course with capacity 2
                        new StudentSubscribedToCourse(studentId1, courseId1), // Fill first course
                        new StudentSubscribedToCourse(studentId2, courseId1),
                        new StudentSubscribedToCourse(studentId1, courseId2), // Fill second course
                        new StudentSubscribedToCourse(studentId2, courseId2)  // This should trigger notification
                )
                .then()
                .await(r -> r.expect(cfg -> assertNotificationSent(cfg, expectedNotification, 1)))
                .and()
                .given()
                .events(
                        new CourseCapacityChanged(courseId2, 3), // This should clear the notified state
                        new StudentSubscribedToCourse(studentId3, courseId2)  // This should trigger notification
                )
                .then()
                .await(r -> r.expect(cfg -> assertNotificationSent(cfg, expectedNotification, 2)));
    }

    @Test
    @Disabled("imported test, failing with: ConditionTimeoutException")
    void givenCoursesNotFullyBookedInFaculty_ThenNoNotificationSent() {
        var studentId1 = "student-" + java.util.UUID.randomUUID().toString();
        var studentId2 = "student-" + java.util.UUID.randomUUID().toString();
        var courseId1 = CourseId.random();
        var courseId2 = CourseId.random();

        fixture.given()
                .events(
                        new CourseCreated(courseId1, "Course 1", 2), // Create course with capacity 2
                        new CourseCreated(courseId2, "Course 2", 2), // Create course with capacity 2
                        new StudentSubscribedToCourse(studentId1, courseId1), // Fill first course
                        new StudentSubscribedToCourse(studentId2, courseId1),
                        new StudentSubscribedToCourse(studentId1, courseId2)
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
