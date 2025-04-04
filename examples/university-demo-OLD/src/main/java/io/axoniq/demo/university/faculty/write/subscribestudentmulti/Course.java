package io.axoniq.demo.university.faculty.write.subscribestudentmulti;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.CourseCapacityChanged;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentSubscribed;
import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.Tag;

import java.util.ArrayList;
import java.util.List;

@EventSourcedEntity(tagKey = FacultyTags.COURSE_ID)
class Course {

    private CourseId id;
    private int capacity = 0;
    private final List<StudentId> studentsSubscribed = new ArrayList<>();

    @EventSourcingHandler
    void handle(CourseCreated event) {
        id = new CourseId(event.courseId());
        capacity = event.capacity();
    }

    @EventSourcingHandler
    void handle(CourseCapacityChanged event) {
        id = new CourseId(event.courseId());
        capacity = event.capacity();
    }

    @EventSourcingHandler
    void handle(StudentSubscribed event) {
        studentsSubscribed.add(new StudentId(event.studentId()));
    }

    CourseId id() {
        return id;
    }

    List<StudentId> studentsSubscribed() {
        return List.copyOf(studentsSubscribed);
    }

    @EventCriteriaBuilder
    public static EventCriteria resolveCriteria(CourseId courseId) {
        return EventCriteria.match()
                            .eventsOfAnyType()
                            .withTags(Tag.of(FacultyTags.COURSE_ID, courseId.raw()));
    }
}
