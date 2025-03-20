package io.axoniq.demo.university.faculty.events;

public record StudentSubscribed(String studentId, String courseId) {

    public static final String TYPE = "StudentSubscribed";
}
