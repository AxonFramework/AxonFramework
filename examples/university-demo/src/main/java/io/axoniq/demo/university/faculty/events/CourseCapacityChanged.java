package io.axoniq.demo.university.faculty.events;

public record CourseCapacityChanged(String id, int capacity) {

    public static final String TYPE = "CourseCapacityChanged";
}