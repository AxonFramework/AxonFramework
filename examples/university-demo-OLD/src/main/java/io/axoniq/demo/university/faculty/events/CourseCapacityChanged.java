package io.axoniq.demo.university.faculty.events;

public record CourseCapacityChanged(String courseId, int capacity) {

    public static final String TYPE = "CourseCapacityChanged";
}