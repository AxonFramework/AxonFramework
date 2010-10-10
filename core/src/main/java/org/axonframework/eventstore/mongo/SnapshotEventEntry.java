package org.axonframework.eventstore.mongo;

import org.axonframework.domain.DomainEvent;
import org.axonframework.eventstore.EventSerializer;

/**
 * @author Jettro Coenradie
 */
class SnapshotEventEntry extends AbstractEventEntry {

    public SnapshotEventEntry(String type, DomainEvent event, EventSerializer eventSerializer) {
        super(type, event, eventSerializer);
    }

    public SnapshotEventEntry(String aggregateIdentifier, long sequenceNumber, byte[] serializedEvent, String timeStamp, String type) {
        super(aggregateIdentifier, sequenceNumber, serializedEvent, timeStamp, type);
    }
}
