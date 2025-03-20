package io.axoniq.demo.university.faculty.events;

public record CourseRenamed(String courseId, String newName) {

    public static final String TYPE = "CourseRenamed";
}
