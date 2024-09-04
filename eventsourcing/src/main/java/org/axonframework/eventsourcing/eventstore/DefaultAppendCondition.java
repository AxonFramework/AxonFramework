package org.axonframework.eventsourcing.eventstore;

/**
 * Default implementation of the {@link AppendCondition}, using the given {@code consistencyMarker} and {@code criteria}
 * as output for the {@link #consistencyMarker()} and {@link #criteria()} operations respectively.
 *
 * @param consistencyMarker The {@code long} to return on the {@link #consistencyMarker()} operation.
 * @param criteria          The {@link EventCriteria} to return on the {@link #criteria()} operation.
 * @author Steven van Beelen
 * @since 5.0.0
 */
record DefaultAppendCondition(
        long consistencyMarker,
        EventCriteria criteria
) implements AppendCondition {

    @Override
    public AppendCondition with(SourcingCondition condition) {
        return new DefaultAppendCondition(
                condition.end()
                         .stream()
                         .map(end -> Math.max(end, consistencyMarker))
                         .findFirst()
                         .orElse(consistencyMarker),
                criteria.combine(condition.criteria())
        );
    }

    @Override
    public AppendCondition withMarker(long consistencyMarker) {
        return new DefaultAppendCondition(Math.min(consistencyMarker, this.consistencyMarker), criteria);
    }
}
