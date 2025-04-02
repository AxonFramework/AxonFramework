package io.axoniq.demo.university.faculty.events;

public record CourseRenamed(String courseId, String name) {

    public static final String TYPE = "CourseRenamed";
}
