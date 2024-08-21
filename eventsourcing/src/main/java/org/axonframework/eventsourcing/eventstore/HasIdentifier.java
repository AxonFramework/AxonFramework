package org.axonframework.eventsourcing.eventstore;

import java.util.Set;

/**
 * An {@link EventCriteria} implementation dedicated towards a single identifier. Will construct a single {@link Tag}
 * with the given {@code key} and {@code value} as its input respectively.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
class HasIdentifier implements EventCriteria {

    private final Tag identifierTag;

    HasIdentifier(String key, String value) {
        this.identifierTag = new Tag(key, value);
    }

    @Override
    public Set<String> types() {
        return Set.of();
    }

    @Override
    public Set<Tag> tags() {
        return Set.of(identifierTag);
    }
}
