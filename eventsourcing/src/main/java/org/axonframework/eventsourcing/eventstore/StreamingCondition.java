package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.TrackingToken;

import javax.annotation.Nullable;

/**
 * Interface describing the condition to {@link StreamableEventSource#open(String, StreamingCondition) stream} events
 * from an Event Store.
 * <p>
 * This condition has a mandatory {@link #position()} that dictates from what point streaming should commence.
 * Additionally, an {@link #criteria()} can be set to filter the stream of events.
 *
 * @author Marco Amann
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface StreamingCondition {

    /**
     * Constructs a simple {@link StreamingCondition} that starts streaming from the given {@code position}. When the
     * {@code position} is {@code null} streaming will start from the beginning of the Event Store.
     *
     * @param position The {@link TrackingToken} describing the position to start streaming from.
     * @return A simple {@link StreamingCondition} that starts streaming from the given {@code position}.
     */
    static StreamingCondition startingFrom(@Nullable TrackingToken position) {
        return new StartingFrom(position);
    }

    /**
     * The position as a {@link TrackingToken} to start streaming from.
     *
     * @return The position as a {@link TrackingToken} to start streaming from.
     */
    TrackingToken position();

    /**
     * The {@link EventCriteria} used to filter the stream of events. Defaults to
     * {@link EventCriteria#noCriteria() no criteria}, hence allowing all events
     *
     * @return The {@link EventCriteria} used to filter the stream of events.
     */
    default EventCriteria criteria() {
        return EventCriteria.noCriteria();
    }

    /**
     * Combines the {@link #criteria()} of {@code this} {@link StreamingCondition} with the given {@code criteria}.
     *
     * @param criteria The {@link EventCriteria} to combine with the {@link #criteria()} of {@code this}
     *                 {@link StreamingCondition}.
     * @return A {@link StreamingCondition} that combined the given {@code criteria} with the {@link #criteria()} of
     * {@code this} {@link StreamingCondition}.
     */
    StreamingCondition with(EventCriteria criteria);
}
