package io.axoniq.demo.university.faculty.events;

public record StudentEnrolledFaculty(String studentId, String firstName, String lastName) {

    public static final String TYPE = "StudentEnrolled";
}
