package org.axonframework.eventsourcing.eventstore;

import java.util.Set;

/**
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class NoEventCriteria implements EventCriteria {

    /**
     *
     */
    public static final NoEventCriteria INSTANCE = new NoEventCriteria();

    @Override
    public Set<String> types() {
        return Set.of();
    }

    @Override
    public Set<Tag> tags() {
        return Set.of();
    }
}
