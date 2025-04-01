package io.axoniq.demo.university.faculty.write;

import java.util.UUID;

public record StudentId(String raw) {

    private final static String ENTITY_TYPE = "Student";

    public StudentId {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Course ID cannot be null or empty");
        }
        raw = withType(raw);
    }

    public static StudentId of(String raw) {
        return new StudentId(raw);
    }

    public static StudentId random() {
        return new StudentId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return raw;
    }

    private static String withType(String id) {
        return id.startsWith(ENTITY_TYPE + ":") ? id : ENTITY_TYPE + ":" + id;
    }
}