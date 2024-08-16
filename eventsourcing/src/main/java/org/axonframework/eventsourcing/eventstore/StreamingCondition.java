package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.TrackingToken;

/**
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface StreamingCondition {

    /**
     *
     * @return
     */
    TrackingToken position();

    /**
     *
     * @return
     */
    default EventCriteria criteria() {
        return NoEventCriteria.INSTANCE;
    }

    /**
     *
     * @param token
     * @return
     */
    static StreamingCondition streamingFrom(TrackingToken token) {
        return new StreamingFrom(token);
    }
}
