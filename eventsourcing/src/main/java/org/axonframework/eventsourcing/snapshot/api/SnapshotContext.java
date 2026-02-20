package org.axonframework.eventsourcing.snapshot.api;

import org.axonframework.eventsourcing.eventstore.Position;

/**
 * Represents the lifecycle context for a specific entity.
 * <p>
 * A {@code Context} is returned when creating or loading an entity snapshot.
 * It provides access to the snapshot and a callback to be invoked when the entity
 * has been fully evolved from replayed events.
 *
 * @param <E> the type of the entity
 * @author John Hendrikx
 * @since 5.1.0
 */
public interface SnapshotContext<E> {

    /**
     * Returns the snapshot of the entity, if available.
     * <p>
     * May return {@code null} if no snapshot exists. The snapshot can be used
     * to initialize the entity before event replay.
     *
     * @return the snapshot of the entity, or {@code null} if none exists
     */
    Snapshot snapshot();

    /**
     * Called immediately after an entity has been fully evolved from its snapshot and
     * any subsequent events.
     * <p>
     * This callback allows the {@code Snapshotter} to:
     * <ul>
     *     <li>Decide whether to persist a new snapshot</li>
     *     <li>Record metrics such as the number of events applied or evolution duration</li>
     *     <li>Perform any additional bookkeeping related to the entity’s lifecycle</li>
     * </ul>
     * <p>
     * This call should not block. Blocking operations should be performed asynchronously.
     *
     * @param entity the fully evolved entity state, cannot be {@code null}
     * @param position the last processed position in the event stream, cannot be {@code null}
     * @param evolveStepCount the number of events applied to the entity since the snapshot, cannot be negative
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code evolveStepCount} is negative
     */
    void entityEvolved(E entity, Position position, long evolveStepCount);
}