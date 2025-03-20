package io.axoniq.demo.university.faculty.events;

public record StudentUnsubscribed(String studentId, String courseId) {

    public static final String TYPE = "StudentUnsubscribed";

}
