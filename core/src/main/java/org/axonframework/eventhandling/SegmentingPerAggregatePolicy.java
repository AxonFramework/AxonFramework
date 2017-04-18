package org.axonframework.eventhandling;

import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Christophe Bouhier
 */
public class SegmentingPerAggregatePolicy extends SequentialPerAggregatePolicy {

    protected long toLong(String uuidAsString) {
        final UUID uuid = UUID.fromString(uuidAsString);
        return uuid.getLeastSignificantBits();
    }

    public boolean matches(Segment segment, TrackedEventMessage<?> trackedEventMessage) {
        final Object sequenceIdentifierFor = getSequenceIdentifierFor(trackedEventMessage);
        if (Objects.nonNull(sequenceIdentifierFor)) {
            if (sequenceIdentifierFor instanceof String) {
                final long segmentIdentifierAsLong = toLong((String) sequenceIdentifierFor);
                return segment.matches(segmentIdentifierAsLong);
            }
        }
        // when null, or when not matching the provided segment.
        return false;
    }
}
