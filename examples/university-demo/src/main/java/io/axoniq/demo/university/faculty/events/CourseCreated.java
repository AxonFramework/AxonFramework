package io.axoniq.demo.university.faculty.events;

public record CourseCreated(String courseId, String name, int capacity) {

    public static final String TYPE = "CourseCreated";
}
