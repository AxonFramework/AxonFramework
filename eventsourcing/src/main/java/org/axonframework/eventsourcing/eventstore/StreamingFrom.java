package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.TrackingToken;

import javax.annotation.Nullable;

/**
 * An implementation of the {@link StreamingCondition} that will start
 * {@link AsyncEventStore#stream(StreamingCondition) streaming} from the given {@code position}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
class StreamingFrom implements StreamingCondition {

    private final TrackingToken token;

    /**
     * Constructs a {@link StreamingFrom} {@link StreamingCondition}, using the given {@code position} as the start
     * point for streaming.
     *
     * @param position The {@link TrackingToken} describing the position to start streaming from.
     */
    StreamingFrom(@Nullable TrackingToken position) {
        this.token = position;
    }

    @Override
    public TrackingToken position() {
        return token;
    }
}
