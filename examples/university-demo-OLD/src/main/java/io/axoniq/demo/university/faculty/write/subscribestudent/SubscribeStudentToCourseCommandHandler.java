package io.axoniq.demo.university.faculty.write.subscribestudent;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.CourseCapacityChanged;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.faculty.events.StudentUnsubscribedFromCourse;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.messaging.MessageType;
import org.axonframework.modelling.annotation.InjectEntity;

import java.util.List;
import java.util.stream.Collectors;

class SubscribeStudentToCourseCommandHandler {

    private static final int MAX_COURSES_PER_STUDENT = 3;

    @CommandHandler
    void handle(
            SubscribeStudentToCourse command,
            @InjectEntity State state,
            EventAppender eventAppender
    ) {
        var events = decide(command, state);
        eventAppender.append(events);
    }

    private List<StudentSubscribedToCourse> decide(SubscribeStudentToCourse command, State state) {
        assertStudentEnrolledFaculty(state);
        assertStudentNotSubscribedToTooManyCourses(state);
        assertCourseExists(state);
        assertEnoughVacantSpotsInCourse(state);
        assertStudentNotAlreadySubscribed(state);

        return List.of(new StudentSubscribedToCourse(command.studentId(), command.courseId()));
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
            this.courseId =event.courseId();
            this.courseCapacity = event.capacity();
        }

        @EventSourcingHandler
        void evolve(CourseCapacityChanged event) {
            this.courseCapacity = event.capacity();
        }

        @EventSourcingHandler
        void evolve(StudentEnrolledInFaculty event) {
            this.studentId = event.studentId();
        }

        @EventSourcingHandler
        void evolve(StudentSubscribedToCourse event) {
            var subscribingStudentId = event.studentId();
            var subscribedCourseId = event.courseId();
            if (subscribedCourseId.equals(courseId)) {
                noOfStudentsSubscribedToCourse++;
            }
            if (subscribingStudentId.equals(studentId)) {
                noOfCoursesStudentSubscribed++;
            }
            if (subscribingStudentId.equals(studentId) && subscribedCourseId.equals(courseId)) {
                alreadySubscribed = true;
            }
        }

        @EventSourcingHandler
        void evolve(StudentUnsubscribedFromCourse event) {
            var subscribingStudentId = event.studentId();
            var subscribedCourseId = event.courseId();
            if (subscribedCourseId.equals(courseId)) {
                noOfStudentsSubscribedToCourse--;
            }
            if (subscribingStudentId.equals(studentId)) {
                noOfCoursesStudentSubscribed--;
            }
            if (subscribingStudentId.equals(studentId) && subscribedCourseId.equals(courseId)) {
                alreadySubscribed = false;
            }
        }

        @EventCriteriaBuilder
        private static EventCriteria resolveCriteria(SubscriptionId id) {
            var courseId = id.courseId().toString();
            var studentId = id.studentId().toString();
            return EventCriteria.either(
                    EventCriteria
                            .havingTags(Tag.of(FacultyTags.COURSE_ID, courseId))
                            .andBeingOneOfTypes(
                                    CourseCreated.class.getName(),
                                    CourseCapacityChanged.class.getName(),
                                    StudentSubscribedToCourse.class.getName(),
                                    StudentUnsubscribedFromCourse.class.getName()
                            ),
                    EventCriteria
                            .havingTags(Tag.of(FacultyTags.STUDENT_ID, studentId))
                            .andBeingOneOfTypes(
                                    StudentEnrolledInFaculty.class.getName(),
                                    StudentSubscribedToCourse.class.getName(),
                                    StudentUnsubscribedFromCourse.class.getName()
                            )
            );
        }
    }
}
