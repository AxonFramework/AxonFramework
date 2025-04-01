package io.axoniq.demo.university.faculty.write;

import java.util.UUID;

public record CourseId(String raw) {

    private final static String ENTITY_TYPE = "Course";

    public CourseId {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Course ID cannot be null or empty");
        }
        raw = withType(raw);
    }

    public static CourseId of(String raw) {
        return new CourseId(raw);
    }

    public static CourseId random() {
        return new CourseId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return raw;
    }

    private static String withType(String id) {
        return id.startsWith(ENTITY_TYPE + ":") ? id : ENTITY_TYPE + ":" + id;
    }
}