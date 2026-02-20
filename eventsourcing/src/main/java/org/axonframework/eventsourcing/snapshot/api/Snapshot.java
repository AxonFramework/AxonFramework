package org.axonframework.eventsourcing.snapshot.api;

import jakarta.annotation.Nonnull;
import org.axonframework.eventsourcing.eventstore.Position;

import java.util.Objects;

/**
 * Represents a snapshot of an entity at a specific position in the event stream.
 *
 * @param position the position in the event stream corresponding to the snapshot, never {@code null}
 * @param entity the state of the entity at this snapshot, never {@code null}
 * @throws NullPointerException if any argument is {@code null}
 * @author John Hendrikx
 * @since 5.1.0
 */
public record Snapshot(@Nonnull Position position, @Nonnull Object entity) {

    /**
     * Constructs a new instance.
     *
     * @throws NullPointerException when any argument is {@code null}
     */
    public Snapshot {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(entity, "entity");
    }
}
