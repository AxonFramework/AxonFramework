package org.axonframework.eventsourcing.eventstore;

/**
 * Test helper class to access package private constructor.
 */
public class GlobalIndexPositions {

    /**
     * Constructs a new {@link GlobalIndexPosition} with the given index.
     *
     * @param index an index
     * @return a new {@link GlobalIndexPosition}, never {@code null}
     */
    public static GlobalIndexPosition of(long index) {
        return new GlobalIndexPosition(index);
    }
}
