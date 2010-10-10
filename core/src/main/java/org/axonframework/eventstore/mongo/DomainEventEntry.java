package org.axonframework.eventstore.mongo;

import org.axonframework.domain.DomainEvent;
import org.axonframework.eventstore.EventSerializer;

/**
 * Mongo compliant wrapper around a DomainEvent. It wraps a DomainEvent by extracting some of the information needed to
 * base searched on, and stores the {@link DomainEvent} itself as a serialized object using am {@link
 * org.axonframework.eventstore.EventSerializer}
 *
 * @author Jettro Coenradie
 * @since 0.7
 */
public class DomainEventEntry extends AbstractEventEntry {

    public DomainEventEntry(String type, DomainEvent event, EventSerializer eventSerializer) {
        super(type, event, eventSerializer);
    }

    public DomainEventEntry(String aggregateIdentifier, long sequenceNumber, String serializedEvent, String timeStamp, String type) {
        super(aggregateIdentifier, sequenceNumber, serializedEvent, timeStamp, type);
    }
}
