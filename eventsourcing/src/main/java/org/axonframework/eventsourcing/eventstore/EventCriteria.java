package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;

import java.util.Set;

/**
 * Interface describing criteria to be taken into account when reading or appending
 * {@link org.axonframework.eventhandling.EventMessage EventMessages} to an {@link EventStore}.
 *
 * @author Steven van Beelen
 * @author Milan Savic
 * @author Marco Amann
 * @since 5.0.0
 */
public interface EventCriteria {

    /**
     * @return
     */
    Set<String> types();

    /**
     * @return
     */
    Set<Tag> tags();

    /**
     *
     * @param identifier
     * @return
     */
    static EventCriteria hasIdentifier(String identifier) {
        return new HasIdentifier(identifier);
    }

    // TODO what are these again...?
    // Labels/Tags/Indices/...
    record Tag(String key, String value) {

    }
}
