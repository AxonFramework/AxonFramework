package io.axoniq.demo.university.faculty.write.subscribestudentmulti;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.CourseCapacityChanged;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentEnrolledFaculty;
import io.axoniq.demo.university.faculty.events.StudentSubscribed;
import io.axoniq.demo.university.faculty.events.StudentUnsubscribed;
import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;
import io.axoniq.demo.university.faculty.write.subscribestudent.SubscribeStudent;
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

    private static final int MAX_COURSES_PER_STUDENT = 10;

    @CommandHandler
    public void handle(
            SubscribeStudent command,
            @InjectEntity Course course,
            @InjectEntity Student student,
            EventSink eventSink,
            ProcessingContext processingContext
    ) {
        var events = decide(command, course, student);
        eventSink.publish(processingContext, toMessages(events));
    }

    private List<StudentSubscribed> decide(SubscribeStudent command, Course course, Student student) {
        assertStudentEnrolledFaculty(student);
        assertStudentNotSubscribedToTooManyCourses(student);
        assertCourseExists(course);
        assertEnoughVacantSpotsInCourse(course);
        assertStudentNotAlreadySubscribed(course);

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

    private void assertStudentEnrolledFaculty(Student student) {
        var studentId = student.id();
        if (studentId == null) {
            throw new RuntimeException("Student with given id never enrolled the faculty");
        }
    }

    public void assertStudentNotSubscribedToTooManyCourses(Student student) {
        var noOfCoursesStudentSubscribed = student.subscribedCourses().size();
        if (noOfCoursesStudentSubscribed >= MAX_COURSES_PER_STUDENT) {
            throw new RuntimeException("Student subscribed to too many courses");
        }
    }

    public void assertEnoughVacantSpotsInCourse(State state) {
        var noOfStudentsSubscribedToCourse = state.noOfStudentsSubscribedToCourse;
        var courseCapacity = state.courseCapacity;
        if (noOfStudentsSubscribedToCourse >= courseCapacity) {
            throw new RuntimeException("Course is fully booked");
        }
    }

    public void assertStudentNotAlreadySubscribed(State state) {
        var alreadySubscribed = state.alreadySubscribed;
        if (alreadySubscribed) {
            throw new RuntimeException("Student already subscribed to this course");
        }
    }

    public void assertCourseExists(State state) {
        var courseId = state.courseId;
        if (courseId == null) {
            throw new RuntimeException("Course with given id does not exist");
        }
    }

    @EventSourcedEntity
    public static class State {

        private StudentId studentId;
        private CourseId courseId;
        private int courseCapacity = 0;

        private int noOfCoursesStudentSubscribed = 0;
        private int noOfStudentsSubscribedToCourse = 0;

        private boolean alreadySubscribed = false;

        @EventSourcingHandler
        public void evolve(CourseCreated event) {
            this.courseId = new CourseId(event.courseId());
            this.courseCapacity = event.capacity();
        }

        @EventSourcingHandler
        public void evolve(StudentEnrolledFaculty event) {
            this.studentId = new StudentId(event.studentId());
        }

        @EventSourcingHandler
        public void evolve(CourseCapacityChanged event) {
            this.courseCapacity = event.capacity();
        }

        @EventSourcingHandler
        public void evolve(StudentSubscribed event) {
            var enrolledStudentId = new StudentId(event.studentId());
            var enrolledCourseId = new CourseId(event.courseId());
            if (enrolledStudentId.equals(studentId) && enrolledCourseId.equals(courseId)) {
                alreadySubscribed = true;
            } else if (enrolledStudentId.equals(studentId)) {
                noOfCoursesStudentSubscribed++;
            } else {
                noOfStudentsSubscribedToCourse++;
            }
        }

        @EventSourcingHandler
        public void evolve(StudentUnsubscribed event) {
            var enrolledStudentId = new StudentId(event.studentId());
            var enrolledCourseId = new CourseId(event.courseId());
            if (enrolledStudentId.equals(studentId) && enrolledCourseId.equals(courseId)) {
                alreadySubscribed = false;
            } else if (enrolledStudentId.equals(studentId)) {
                noOfCoursesStudentSubscribed--;
            } else {
                noOfStudentsSubscribedToCourse--;
            }
        }

        @EventCriteriaBuilder
        public static EventCriteria resolveCriteria(
                io.axoniq.demo.university.faculty.write.subscribestudent.SubscriptionId id) {
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
