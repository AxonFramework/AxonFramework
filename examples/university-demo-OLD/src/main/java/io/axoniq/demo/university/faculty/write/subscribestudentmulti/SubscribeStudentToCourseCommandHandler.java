package io.axoniq.demo.university.faculty.write.subscribestudentmulti;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import org.axonframework.commandhandling.annotations.CommandHandler;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotations.InjectEntity;

import java.util.List;

class SubscribeStudentToCourseCommandHandler {

    private static final int MAX_COURSES_PER_STUDENT = 3;

    @CommandHandler
    void handle(
            SubscribeStudentToCourse command,
            @InjectEntity(idProperty = FacultyTags.COURSE_ID) Course course,
            @InjectEntity(idProperty = FacultyTags.STUDENT_ID) Student student,
            EventAppender eventAppender
    ) {
        var events = decide(command, course, student);
        eventAppender.append(events);
    }

    private List<StudentSubscribedToCourse> decide(SubscribeStudentToCourse command, Course course, Student student) {
        assertStudentEnrolledFaculty(student);
        assertStudentNotSubscribedToTooManyCourses(student);
        assertCourseExists(course);
        assertEnoughVacantSpotsInCourse(course);
        assertStudentNotAlreadySubscribed(course, student);

        return List.of(new StudentSubscribedToCourse(command.studentId(), command.courseId()));
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
