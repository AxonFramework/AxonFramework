package org.axonframework.eventsourcing.eventstore;

/**
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class NoAppendCondition implements AppendCondition {

    /**
     *
     */
    public static final NoAppendCondition INSTANCE = new NoAppendCondition();

    @Override
    public long position() {
        return -1;
    }

    @Override
    public EventCriteria criteria() {
        return NoEventCriteria.INSTANCE;
    }
}
