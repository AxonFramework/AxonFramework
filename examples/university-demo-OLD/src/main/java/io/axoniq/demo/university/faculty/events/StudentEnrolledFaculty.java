package io.axoniq.demo.university.faculty.events;

public record StudentEnrolledFaculty(String id, String firstName, String lastName) {

    public static final String TYPE = "StudentEnrolled";
}
