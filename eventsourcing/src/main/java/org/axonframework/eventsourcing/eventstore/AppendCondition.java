package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;

/**
 * Interface describing the consistency boundary condition for
 * {@link org.axonframework.eventhandling.EventMessage EventMessages} when
 * {@link EventStoreTransaction#appendEvent(EventMessage) appending} them to an Event Store.
 *
 * @author Steven van Beelen
 * @author Milan Savic
 * @author Marco Amann
 * @since 5.0.0
 */
public interface AppendCondition {

    /**
     * Returns an {@link AppendCondition} that has no criteria nor consistency marker.
     * <p>
     * Only use this {@code AppendCondition} when appending events that <em>do not</em> partake in the consistency
     * boundary of any model(s).
     *
     * @return An {@link AppendCondition} that has no criteria nor consistency marker.
     */
    static AppendCondition none() {
        return NoAppendCondition.INSTANCE;
    }

    /**
     * Constructs a {@link AppendCondition} based on the given {@code condition}.
     * <p>
     * Uses the {@link SourcingCondition#end()} as the {@link #consistencyMarker()} and defaults to {@code -1L} when it
     * isn't present. The {@link SourcingCondition#criteria()} is taken as is for the {@link #criteria()} operation.
     *
     * @param condition The {@link SourcingCondition} to base an {@link AppendCondition}.
     * @return An {@link AppendCondition} based on the given {@code condition}.
     */
    static AppendCondition from(SourcingCondition condition) {
        return new DefaultAppendCondition(condition.end().orElse(-1L), condition.criteria());
    }

    /**
     * Returns the position in the event store until which the {@link #criteria()} should be validated against.
     * <p>
     * Appending will fail when there are events appended after this point that match the provided
     * {@link EventCriteria}.
     *
     * @return The position in the event store until which the {@link #criteria()} should be validated against.
     */
    long consistencyMarker();

    /**
     * Returns the {@link EventCriteria} to validate until the provided {@link #consistencyMarker()}.
     * <p>
     * Appending will fail when there are events appended after this point that match the criteria.
     *
     * @return The {@link EventCriteria} to validate until the provided {@link #consistencyMarker()}.
     */
    EventCriteria criteria();

    /**
     * Combines the {@code this AppendCondition} with the given {@code condition}.
     * <p>
     * Typically attached the {@link SourcingCondition#criteria()} with {@code this} condition's {@link #criteria()} and
     * picks the largest value among the {@link #consistencyMarker()} and {@link SourcingCondition#end()} values.
     *
     * @param condition The {@link SourcingCondition} to combine with {@code this AppendCondition}.
     * @return An {@link AppendCondition} combined with the given {@code condition}.
     */
    AppendCondition with(SourcingCondition condition);
}
