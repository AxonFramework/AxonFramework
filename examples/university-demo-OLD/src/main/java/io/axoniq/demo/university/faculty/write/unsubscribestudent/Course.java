package io.axoniq.demo.university.faculty.write.unsubscribestudent;

import io.axoniq.demo.university.faculty.events.StudentSubscribed;
import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;
import org.axonframework.eventsourcing.EventSourcingHandler;

import java.util.HashSet;
import java.util.Set;

class Course {

    CourseId courseId;
    Set<StudentId> studentsSubscribed = new HashSet<>();

    Course(CourseId courseId) {
        this.courseId = courseId;
    }

    @EventSourcingHandler
    public void handle(StudentSubscribed event) {
        var courseId = new CourseId(event.courseId());
        if (courseId.equals(this.courseId)) {
            var studentId = new StudentId(event.studentId());
            studentsSubscribed.add(studentId);
        }
    }
}
