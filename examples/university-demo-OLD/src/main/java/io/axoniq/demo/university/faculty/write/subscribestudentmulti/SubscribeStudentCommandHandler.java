package io.axoniq.demo.university.faculty.write.subscribestudentmulti;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.StudentSubscribed;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.annotation.InjectEntity;

import java.util.List;
import java.util.stream.Collectors;

class SubscribeStudentCommandHandler {

    private static final int MAX_COURSES_PER_STUDENT = 10;

    @CommandHandler
    void handle(
            SubscribeStudent command,
            @InjectEntity(idProperty = FacultyTags.COURSE_ID) Course course,
            @InjectEntity(idProperty = FacultyTags.STUDENT_ID) Student student,
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
        assertStudentNotAlreadySubscribed(course, student);

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

    private void assertStudentNotSubscribedToTooManyCourses(Student student) {
        var noOfCoursesStudentSubscribed = student.subscribedCourses().size();
        if (noOfCoursesStudentSubscribed >= MAX_COURSES_PER_STUDENT) {
            throw new RuntimeException("Student subscribed to too many courses");
        }
    }

    private void assertEnoughVacantSpotsInCourse(Course course) {
        var noOfStudentsSubscribedToCourse = course.studentsSubscribed().size();
        var courseCapacity = course.capacity();
        if (noOfStudentsSubscribedToCourse >= courseCapacity) {
            throw new RuntimeException("Course is fully booked");
        }
    }

    private void assertStudentNotAlreadySubscribed(Course course, Student student) {
        var alreadySubscribed = course.studentsSubscribed().contains(student.id());
        if (alreadySubscribed) {
            throw new RuntimeException("Student already subscribed to this course");
        }
    }

    private void assertCourseExists(Course course) {
        var courseId = course.id();
        if (courseId == null) {
            throw new RuntimeException("Course with given id does not exist");
        }
    }
}
