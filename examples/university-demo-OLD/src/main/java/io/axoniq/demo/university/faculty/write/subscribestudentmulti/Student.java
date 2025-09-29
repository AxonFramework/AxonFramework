package io.axoniq.demo.university.faculty.write.subscribestudentmulti;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.eventsourcing.annotations.EventSourcingHandler;
import org.axonframework.eventsourcing.annotations.EventSourcedEntity;
import org.axonframework.eventsourcing.annotations.reflection.EntityCreator;

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
