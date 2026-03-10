package org.axonframework.examples.demo.university.faculty.write.subscribestudentmulti;

import org.axonframework.examples.demo.university.faculty.FacultyTags;
import org.axonframework.examples.demo.university.faculty.events.StudentEnrolledInFaculty;
import org.axonframework.examples.demo.university.faculty.events.StudentSubscribedToCourse;
import org.axonframework.examples.demo.university.shared.ids.CourseId;
import org.axonframework.examples.demo.university.shared.ids.StudentId;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;

import java.util.ArrayList;
import java.util.List;

@EventSourcedEntity(tagKey = FacultyTags.STUDENT_ID)
class Student {

    private StudentId id;
    private final List<CourseId> subscribedCourses = new ArrayList<>();

    @EntityCreator
    public Student() {
    }

    @EventSourcingHandler
    void evolve(StudentEnrolledInFaculty event) {
        id = event.studentId();
    }

    @EventSourcingHandler
    void evolve(StudentSubscribedToCourse event) {
        subscribedCourses.add(event.courseId());
    }

    StudentId id() {
        return id;
    }

    List<CourseId> subscribedCourses() {
        return List.copyOf(subscribedCourses);
    }

}
