package io.axoniq.demo.university.faculty.write.subscribestudentmulti;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;

import java.util.ArrayList;
import java.util.List;

@EventSourcedEntity(tagKey = FacultyTags.STUDENT_ID)
class Student {

    private StudentId id;
    private final List<CourseId> subscribedCourses = new ArrayList<>();

    @EventSourcingHandler
    void evolve(StudentEnrolledInFaculty event) {
        id = new StudentId(event.studentId());
    }

    @EventSourcingHandler
    void evolve(StudentSubscribedToCourse event) {
        subscribedCourses.add(new CourseId(event.courseId()));
    }

    StudentId id() {
        return id;
    }

    List<CourseId> subscribedCourses() {
        return List.copyOf(subscribedCourses);
    }

}
