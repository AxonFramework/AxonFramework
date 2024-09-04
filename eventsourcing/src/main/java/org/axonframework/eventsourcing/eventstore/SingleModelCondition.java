package org.axonframework.eventsourcing.eventstore;

import java.util.OptionalLong;

/**
 * A {@link SourcingCondition} implementation intended to source a single model instance, based on the given
 * {@code identifierName} to {@code identifierValue} pair.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
class SingleModelCondition implements SourcingCondition {

    private final EventCriteria identifierCriteria;
    private final Long start;
    private final OptionalLong end;

    /**
     * Constructs a {@link SingleModelCondition} using the given {@code identifierName} and {@code identifierValue} to
     * construct an {@link EventCriteria#hasIdentifier(String, String) identifier-based EventCriteria}. The
     * {@code start} and {@code end} refer to the window of events that is of interest to this
     * {@link SourcingCondition}.
     *
     * @param identifierName  The name of the identifier of the model to source.
     * @param identifierValue The value of the identifier of the model to source.
     * @param start           The start position in the event sequence to retrieve of the model to source.
     * @param end             The end position in the event sequence to retrieve of the model to source.
     */
    SingleModelCondition(String identifierName, String identifierValue, Long start, Long end) {
        this.identifierCriteria = EventCriteria.hasIdentifier(identifierName, identifierValue);
        this.start = start != null ? start : -1;
        this.end = end == null ? OptionalLong.empty() : OptionalLong.of(end);
    }

    @Override
    public EventCriteria criteria() {
        return this.identifierCriteria;
    }

    @Override
    public long start() {
        return this.start;
    }

    @Override
    public OptionalLong end() {
        return this.end;
    }
}
