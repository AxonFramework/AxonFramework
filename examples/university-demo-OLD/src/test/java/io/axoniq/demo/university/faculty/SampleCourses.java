package io.axoniq.demo.university.faculty;

import io.axoniq.demo.university.faculty.write.CourseId;

public class SampleCourses {

    public static final int DEFAULT_CAPACITY = 42;

    public record Course(CourseId id, String name, int capacity) {

        public Course withCapacity(int capacity) {
            return new Course(id, name, capacity);
        }
    }

    public static Course programming() {
        return new Course(CourseId.random(), "Programming", DEFAULT_CAPACITY);
    }

    public static Course eventSourcingInPractice() {
        return new Course(CourseId.random(), "Event Sourcing in Practice", 42);
    }

    public static Course eventSourcingInTheory() {
        return new Course(CourseId.random(), "Event Sourcing in Theory", 42);
    }

    public static Course axonFramework5BeAPro() {
        return new Course(CourseId.random(), "Axon Framework 5: Be a PRO", 42);
    }
}
