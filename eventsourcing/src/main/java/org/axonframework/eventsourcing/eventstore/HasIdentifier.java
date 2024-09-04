package org.axonframework.eventsourcing.eventstore;

import java.util.Set;

/**
 * An {@link EventCriteria} implementation dedicated towards a single identifier.
 * <p>
 * Will construct a single {@link Index} with the given {@code key} and {@code value} as its input respectively.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
class HasIdentifier implements EventCriteria {

    private final Index identifierIndex;

    HasIdentifier(String key, String value) {
        this.identifierIndex = new Index(key, value);
    }

    @Override
    public Set<String> types() {
        return Set.of();
    }

    @Override
    public Set<Index> indices() {
        return Set.of(identifierIndex);
    }
}
