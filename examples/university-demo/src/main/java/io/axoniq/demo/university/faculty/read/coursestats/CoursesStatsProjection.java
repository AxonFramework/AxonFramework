package io.axoniq.demo.university.faculty.read.coursestats;

import io.axoniq.demo.university.faculty.events.*;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.annotation.SequencingPolicy;
import org.axonframework.messaging.eventhandling.sequencing.PropertySequencingPolicy;

@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = {"courseId"})
class CoursesStatsProjection {

    private final CourseStatsRepository repository;

    public CoursesStatsProjection(CourseStatsRepository repository) {
        this.repository = repository;
    }

    @EventHandler
    void handle(CourseCreated event) {
        CoursesStatsReadModel readModel = new CoursesStatsReadModel(
                event.courseId(),
                event.name(),
                event.capacity(),
                0
        );
        repository.save(readModel);
    }

    @EventHandler
    void handle(CourseRenamed event) {
        CoursesStatsReadModel readModel = repository.findByIdOrThrow(event.courseId());
        var updatedReadModel = readModel.name(event.name());
        repository.save(updatedReadModel);
    }

    @EventHandler
    void handle(CourseCapacityChanged event) {
        CoursesStatsReadModel readModel = repository.findByIdOrThrow(event.courseId());
        var updatedReadModel = readModel.capacity(event.capacity());
        repository.save(updatedReadModel);
    }

    @EventHandler
    void handle(StudentSubscribedToCourse event) {
        CoursesStatsReadModel readModel = repository.findByIdOrThrow(event.courseId());
        var updatedReadModel = readModel.subscribedStudents(readModel.subscribedStudents() + 1);
        repository.save(updatedReadModel);
    }

    @EventHandler
    void handle(StudentUnsubscribedFromCourse event) {
        CoursesStatsReadModel readModel = repository.findByIdOrThrow(event.courseId());
        var updatedReadModel = readModel.subscribedStudents(readModel.subscribedStudents() - 1);
        repository.save(updatedReadModel);
    }

}
