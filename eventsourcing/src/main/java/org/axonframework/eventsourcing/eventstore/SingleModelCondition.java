package org.axonframework.eventsourcing.eventstore;

import java.util.OptionalLong;

/**
 * @author Steven van Beelen
 * @since 5.0.0
 */
class SingleModelCondition implements SourcingCondition {

    private final EventCriteria identifierCriteria;
    private final Long start;
    private final Long end;

    /**
     * @param identifier
     * @param start
     * @param end
     */
    SingleModelCondition(String identifier,
                         Long start,
                         Long end) {
        this.identifierCriteria = EventCriteria.hasIdentifier(identifier);
        this.start = start;
        this.end = end;
    }

    @Override
    public EventCriteria criteria() {
        return identifierCriteria;
    }

    @Override
    public long start() {
        return this.start != null ? this.start : -1;
    }

    @Override
    public OptionalLong end() {
        return OptionalLong.of(end);
    }
}
