package org.axonframework.eventsourcing.eventstore;

/**
 * An {@link AppendCondition} implementation that has {@link EventCriteria#noCriteria() no criteria}.
 * <p>
 * Only use this {@code AppendCondition} when appending events that <em>do not</em> partake in the consistency boundary
 * of any model(s).
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
class NoAppendCondition implements AppendCondition {

    /**
     * Default instance of the {@link NoAppendCondition}.
     */
    static final NoAppendCondition INSTANCE = new NoAppendCondition();

    @Override
    public long consistencyMarker() {
        return -1;
    }

    @Override
    public EventCriteria criteria() {
        return EventCriteria.noCriteria();
    }

    @Override
    public AppendCondition with(SourcingCondition condition) {
        return AppendCondition.from(condition);
    }
}
