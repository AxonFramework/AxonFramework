package io.axoniq.demo.university.faculty.write.subscribestudent;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.CourseCapacityChanged;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentEnrolledFaculty;
import io.axoniq.demo.university.faculty.events.StudentSubscribed;
import io.axoniq.demo.university.faculty.events.StudentUnsubscribed;
import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.annotation.InjectEntity;

import java.util.List;
import java.util.stream.Collectors;

class SubscribeStudentCommandHandler {

    private static final int MAX_COURSES_PER_STUDENT = 3;

    @CommandHandler
    void handle(
            SubscribeStudent command,
            @InjectEntity State state,
            EventSink eventSink,
            ProcessingContext processingContext
    ) {
        var events = decide(command, state);
        eventSink.publish(processingContext, toMessages(events));
    }

    private List<StudentSubscribed> decide(SubscribeStudent command, State state) {
        assertStudentEnrolledFaculty(state);
        assertStudentNotSubscribedToTooManyCourses(state);
        assertCourseExists(state);
        assertEnoughVacantSpotsInCourse(state);
        assertStudentNotAlreadySubscribed(state);

        return List.of(new StudentSubscribed(command.studentId().raw(), command.courseId().raw()));
    }

    private static List<EventMessage<?>> toMessages(List<StudentSubscribed> events) {
        return events.stream()
                     .map(SubscribeStudentCommandHandler::toMessage)
                     .collect(Collectors.toList());
    }

    private static EventMessage<?> toMessage(Object payload) {
        return new GenericEventMessage<>(
                new MessageType(payload.getClass()),
                payload
        );
    }

    private void assertStudentEnrolledFaculty(State state) {
        var studentId = state.studentId;
        if (studentId == null) {
            throw new RuntimeException("Student with given id never enrolled the faculty");
        }
    }

    private void assertStudentNotSubscribedToTooManyCourses(State state) {
        var noOfCoursesStudentSubscribed = state.noOfCoursesStudentSubscribed;
        if (noOfCoursesStudentSubscribed >= MAX_COURSES_PER_STUDENT) {
            throw new RuntimeException("Student subscribed to too many courses");
        }
    }

    private void assertEnoughVacantSpotsInCourse(State state) {
        var noOfStudentsSubscribedToCourse = state.noOfStudentsSubscribedToCourse;
        var courseCapacity = state.courseCapacity;
        if (noOfStudentsSubscribedToCourse >= courseCapacity) {
            throw new RuntimeException("Course is fully booked");
        }
    }

    private void assertStudentNotAlreadySubscribed(State state) {
        var alreadySubscribed = state.alreadySubscribed;
        if (alreadySubscribed) {
            throw new RuntimeException("Student already subscribed to this course");
        }
    }

    private void assertCourseExists(State state) {
        var courseId = state.courseId;
        if (courseId == null) {
            throw new RuntimeException("Course with given id does not exist");
        }
    }

    @EventSourcedEntity
    static class State {

        private CourseId courseId;
        private int courseCapacity = 0;
        private int noOfStudentsSubscribedToCourse = 0;

        private StudentId studentId;
        private int noOfCoursesStudentSubscribed = 0;
        private boolean alreadySubscribed = false;

        @EventSourcingHandler
        void evolve(CourseCreated event) {
            this.courseId = new CourseId(event.courseId());
            this.courseCapacity = event.capacity();
        }

        @EventSourcingHandler
        void evolve(StudentEnrolledFaculty event) {
            this.studentId = new StudentId(event.studentId());
        }

        @EventSourcingHandler
        void evolve(CourseCapacityChanged event) {
            this.courseCapacity = event.capacity();
        }

        @EventSourcingHandler
        void evolve(StudentSubscribed event) {
            var subscribingStudentId = new StudentId(event.studentId());
            var subscribedCourseId = new CourseId(event.courseId());
            noOfStudentsSubscribedToCourse++;
            if (subscribingStudentId.equals(studentId)) {
                noOfCoursesStudentSubscribed++;
            }
            if (subscribingStudentId.equals(studentId) && subscribedCourseId.equals(courseId)) {
                alreadySubscribed = true;
            }
        }

        @EventSourcingHandler
        void evolve(StudentUnsubscribed event) {
            var subscribingStudentId = new StudentId(event.studentId());
            var subscribedCourseId = new CourseId(event.courseId());
            noOfStudentsSubscribedToCourse--;
            if (subscribingStudentId.equals(studentId)) {
                noOfCoursesStudentSubscribed--;
            }
            if (subscribingStudentId.equals(studentId) && subscribedCourseId.equals(courseId)) {
                alreadySubscribed = false;
            }
        }

        @EventCriteriaBuilder
        private static EventCriteria resolveCriteria(SubscriptionId id) {
            var courseId = id.courseId().raw();
            var studentId = id.studentId().raw();
            return EventCriteria.either(
                    EventCriteria.match()
                                 .eventsOfTypes(
                                         CourseCreated.class.getName(),
                                         CourseCapacityChanged.class.getName()
                                 ).withTags(Tag.of(FacultyTags.COURSE_ID, courseId)),
                    EventCriteria.match()
                                 .eventsOfTypes(StudentEnrolledFaculty.class.getName())
                                 .withTags(Tag.of(FacultyTags.STUDENT_ID, studentId)),
                    EventCriteria.match()
                                 .eventsOfTypes(
                                         StudentSubscribed.class.getName(),
                                         StudentUnsubscribed.class.getName()
                                 ).withTags(Tag.of(FacultyTags.COURSE_ID, courseId)),
                    EventCriteria.match()
                                 .eventsOfTypes(
                                         StudentSubscribed.class.getName(),
                                         StudentUnsubscribed.class.getName()
                                 ).withTags(Tag.of(FacultyTags.STUDENT_ID, studentId))
            );
        }
    }
}
