package org.axonframework.examples.demo.university.faculty.write.subscribestudentmulti;

import org.axonframework.examples.demo.university.faculty.FacultyTags;
import org.axonframework.examples.demo.university.faculty.events.CourseCapacityChanged;
import org.axonframework.examples.demo.university.faculty.events.CourseCreated;
import org.axonframework.examples.demo.university.faculty.events.StudentSubscribedToCourse;
import org.axonframework.examples.demo.university.shared.ids.CourseId;
import org.axonframework.examples.demo.university.shared.ids.StudentId;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
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
