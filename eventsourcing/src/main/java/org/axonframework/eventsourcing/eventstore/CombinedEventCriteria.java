package org.axonframework.eventsourcing.eventstore;

import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of the {@link EventCriteria} combining two different {@code EventCriteria} instances into a single
 * {@code EventCriteria}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
class CombinedEventCriteria implements EventCriteria {

    private final Set<String> types;
    private final Set<Tag> tags;

    /**
     * Constructs a {@link CombinedEventCriteria} combining the {@link #types()} and {@link #tags()} of the given
     * {@code first} and {@code second} {@link EventCriteria}.
     *
     * @param first  The {@link EventCriteria} to combine with the {@code second} into a single {@code EventCriteria}.
     * @param second The {@link EventCriteria} to combine with the {@code first} into a single {@code EventCriteria}.
     */
    CombinedEventCriteria(EventCriteria first, EventCriteria second) {
        this.types = new HashSet<>(first.types());
        this.types.addAll(second.types());

        this.tags = new HashSet<>(first.tags());
        this.tags.addAll(second.tags());
    }

    @Override
    public Set<String> types() {
        return Set.copyOf(types);
    }

    @Override
    public Set<Tag> tags() {
        return Set.copyOf(tags);
    }
}
