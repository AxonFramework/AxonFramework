package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.TrackingToken;

import javax.annotation.Nullable;

/**
 * An implementation of the {@link StreamingCondition} that will start
 * {@link StreamableEventSource#open(String, StreamingCondition) streaming} from the given {@code position}.
 *
 * @param position The {@link TrackingToken} describing the position to start streaming from.
 * @author Steven van Beelen
 * @since 5.0.0
 */
record StartingFrom(@Nullable TrackingToken position) implements StreamingCondition {

    @Override
    public StreamingCondition with(EventCriteria criteria) {
        return new DefaultStreamingCondition(position, criteria);
    }
}
