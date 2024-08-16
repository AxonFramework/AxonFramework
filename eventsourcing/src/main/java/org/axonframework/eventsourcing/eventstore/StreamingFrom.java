package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.TrackingToken;

/**
 * @author Steven van Beelen
 * @since 5.0.0
 */
class StreamingFrom implements StreamingCondition {

    private final TrackingToken token;

    /**
     * @param token
     */
    public StreamingFrom(TrackingToken token) {
        this.token = token;
    }

    @Override
    public TrackingToken position() {
        return token;
    }
}
