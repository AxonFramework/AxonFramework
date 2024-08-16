package org.axonframework.eventsourcing.eventstore;

import java.util.Set;

/**
 * @author Steven van Beelen
 * @since 5.0.0
 */
class HasIdentifier implements EventCriteria {

    private final String identifier;

    /**
     * @param identifier
     */
    HasIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @Override
    public Set<String> types() {
        return Set.of(identifier);
    }

    @Override
    public Set<Tag> tags() {
        return Set.of();
    }
}
