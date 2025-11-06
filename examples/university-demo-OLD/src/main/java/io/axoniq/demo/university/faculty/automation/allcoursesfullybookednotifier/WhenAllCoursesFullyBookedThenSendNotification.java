package io.axoniq.demo.university.faculty.automation.allcoursesfullybookednotifier;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.Ids;
import io.axoniq.demo.university.faculty.events.*;
import io.axoniq.demo.university.shared.application.notifier.NotificationService;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.FacultyId;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.commandhandling.gateway.CommandDispatcher;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.Tag;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.annotation.InjectEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * This class represents the automation of sending a Command some condition is met.
 * It's a Stateful Event Handler that reacts on multiple events and tracks the state (left spots) of courses.
 * <p>
 * The implementation uses event soured entity to track the state of courses and their capacities.
 * When all courses are determined to be fully booked, a notification is sent to the appropriate recipient.
 * <p>
 * The functionality includes:
 * - Managing the state of courses (availability and subscription levels).
 * - Reacting to events such as course capacity updates, student subscriptions, and unsubscriptions.
 * - Assessing whether all courses are fully booked and sending a notification if necessary.
 */
public class WhenAllCoursesFullyBookedThenSendNotification {

    @EventSourcedEntity
    record State(Map<CourseId, Course> courses, boolean notified) {

        record Course(int capacity, int students) {

            Course capacity(int newCapacity) {
                return new Course(newCapacity, this.students);
            }

            Course studentSubscribed() {
                return new Course(this.capacity, this.students + 1);
            }

            Course studentUnsubscribed() {
                return new Course(this.capacity, this.students - 1);
            }

            boolean isFullyBooked() {
                return students >= capacity;
            }

        }

        @EntityCreator
        State() {
            this(new HashMap<>(), false);
        }

        @EventSourcingHandler
        State evolve(CourseCreated event) {
            courses.put(event.courseId(), new Course(event.capacity(), 0));
            return new State(courses, isNotified());
        }

        @EventSourcingHandler
        State evolve(CourseCapacityChanged event) {
            courses.computeIfPresent(event.courseId(), (id, course) -> course.capacity(event.capacity()));
            return new State(courses, isNotified());
        }

        @EventSourcingHandler
        State evolve(StudentSubscribedToCourse event) {
            courses.computeIfPresent(event.courseId(), (id, course) -> course.studentSubscribed());
            return new State(courses, isNotified());
        }

        @EventSourcingHandler
        State evolve(StudentUnsubscribedFromCourse event) {
            courses.computeIfPresent(event.courseId(), (id, course) -> course.studentUnsubscribed());
            return new State(courses, isNotified());
        }

        @EventSourcingHandler
        State evolve(AllCoursesFullyBookedNotificationSent event) {
            return new State(courses, true);
        }

        private boolean isNotified() {
            if (courses.values().stream().allMatch(State.Course::isFullyBooked)) {
                return notified;
            }
            return false;
        }

        @EventCriteriaBuilder
        private static EventCriteria resolveCriteria(FacultyId facultyId) {
            return EventCriteria.havingTags(Tag.of(FacultyTags.FACULTY_ID, facultyId.toString()))
                    .andBeingOneOfTypes(
                            CourseCreated.class.getName(),
                            CourseCapacityChanged.class.getName(),
                            StudentSubscribedToCourse.class.getName(),
                            StudentUnsubscribedFromCourse.class.getName(),
                            AllCoursesFullyBookedNotificationSent.class.getName()
                    );
        }

    }

    static class AutomationCommandHandler {

        @CommandHandler
        void decide(
                SendAllCoursesFullyBookedNotification command,
                @InjectEntity State state,
                ProcessingContext context
        ) {
            var canNotify = state != null && !state.notified();
            if (canNotify) {
                var notification = new NotificationService.Notification("admin", "All courses are fully booked now.");
                context.component(NotificationService.class).sendNotification(notification);
                var eventAppender = EventAppender.forContext(context);
                eventAppender.append(new AllCoursesFullyBookedNotificationSent(command.facultyId()));
            }
        }
    }

    static class AutomationEventHandler {

        @EventHandler
        CompletableFuture<?> react(
                StudentSubscribedToCourse event,
                CommandDispatcher commandDispatcher,
                ProcessingContext context
        ) {
            var state = context.component(StateManager.class).loadEntity(State.class, Ids.FACULTY_ID, context).join();
            return sendNotificationIfAllCoursesFullyBooked(state, commandDispatcher);
        }

        @EventHandler
        CompletableFuture<?> react(
                CourseCapacityChanged event,
                CommandDispatcher commandDispatcher,
                ProcessingContext context
        ) {
            var state = context.component(StateManager.class).loadEntity(State.class, Ids.FACULTY_ID, context).join();
            return sendNotificationIfAllCoursesFullyBooked(state, commandDispatcher);
        }

        private CompletableFuture<?> sendNotificationIfAllCoursesFullyBooked(
                State state,
                CommandDispatcher commandDispatcher
        ) {
            var automationState = state != null ? state : new State();
            var allCoursesFullyBooked = automationState.courses.values().stream().allMatch(State.Course::isFullyBooked);
            var shouldNotify = allCoursesFullyBooked && !automationState.notified();
            if (!shouldNotify) {
                return CompletableFuture.completedFuture(null);
            }
            return commandDispatcher.send(new SendAllCoursesFullyBookedNotification(Ids.FACULTY_ID), Object.class);
        }

    }

}
