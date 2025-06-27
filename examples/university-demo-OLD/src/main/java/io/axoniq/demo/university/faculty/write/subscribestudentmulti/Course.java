package io.axoniq.demo.university.faculty.write.subscribestudentmulti;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.CourseCapacityChanged;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.shared.ids.CourseId;
import io.axoniq.demo.university.shared.ids.StudentId;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;

import java.util.ArrayList;
import java.util.List;

@EventSourcedEntity(tagKey = FacultyTags.COURSE_ID)
class Course {

    private CourseId id;
    private int capacity = 0;
    private final List<StudentId> studentsSubscribed = new ArrayList<>();

    @EntityCreator
    public Course() {
    }

    @EventSourcingHandler
    void evolve(CourseCreated event) {
        id = event.courseId();
        capacity = event.capacity();
    }

    @EventSourcingHandler
    void evolve(CourseCapacityChanged event) {
        id = event.courseId();
        capacity = event.capacity();
    }

    @EventSourcingHandler
    void evolve(StudentSubscribedToCourse event) {
        studentsSubscribed.add(event.studentId());
    }

    CourseId id() {
        return id;
    }

    int capacity() {
        return capacity;
    }

    List<StudentId> studentsSubscribed() {
        return List.copyOf(studentsSubscribed);
    }

}
