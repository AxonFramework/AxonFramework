package io.axoniq.demo.university.shared.ids;

import java.util.UUID;

public record FacultyId(String raw) {

    private final static String ENTITY_TYPE = "Faculty";

    public FacultyId {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Faculty ID cannot be null or empty");
        }
        raw = withType(raw);
    }

    public static FacultyId of(String raw) {
        return new FacultyId(raw);
    }

    public static FacultyId random() {
        return new FacultyId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return raw;
    }

    private static String withType(String id) {
        return id.startsWith(ENTITY_TYPE + ":") ? id : ENTITY_TYPE + ":" + id;
    }
}