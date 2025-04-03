package io.axoniq.demo.university.faculty.write.unsubscribestudent;

import io.axoniq.demo.university.faculty.events.StudentSubscribed;
import io.axoniq.demo.university.faculty.events.StudentUnsubscribed;
import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;
import org.axonframework.eventsourcing.EventSourcingHandler;

import java.util.HashSet;
import java.util.Set;

class Student {

    StudentId studentId;
    Set<CourseId> subscribedCourses = new HashSet<>();

    Student(StudentId studentId) {
        this.studentId = studentId;
    }

    @EventSourcingHandler
    public void handle(StudentSubscribed event) {
        var studentId = new StudentId(event.studentId());
        if (studentId.equals(this.studentId)) {
            var courseId = new CourseId(event.courseId());
            subscribedCourses.add(courseId);
        }
    }

    @EventSourcingHandler
    public void handle(StudentUnsubscribed event) {
        var studentId = new StudentId(event.studentId());
        if (studentId.equals(this.studentId)) {
            var courseId = new CourseId(event.courseId());
            subscribedCourses.add(courseId);
        }
    }
}
