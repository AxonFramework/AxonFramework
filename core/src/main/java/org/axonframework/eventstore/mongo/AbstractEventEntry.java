package org.axonframework.eventstore.mongo;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.AggregateIdentifierFactory;
import org.axonframework.domain.DomainEvent;
import org.axonframework.eventstore.EventSerializer;
import org.joda.time.LocalDateTime;

/**
 * Data needed by different types of event logs.
 *
 * @author Allard Buijze
 * @author Jettro Coenradie
 * @since 0.7
 */
public class AbstractEventEntry {
    private String aggregateIdentifier;
    private long sequenceNumber;
    private String timeStamp;
    private String type;
    private byte[] serializedEvent;

    protected AbstractEventEntry(String type, DomainEvent event, EventSerializer eventSerializer) {
        this.type = type;
        this.aggregateIdentifier = event.getAggregateIdentifier().toString();
        this.sequenceNumber = event.getSequenceNumber();
        this.serializedEvent = eventSerializer.serialize(event);
        this.timeStamp = event.getTimestamp().toString();
    }

    protected AbstractEventEntry(String aggregateIdentifier, long sequenceNumber, byte[] serializedEvent, String timeStamp, String type) {
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
        this.serializedEvent = serializedEvent;
        this.timeStamp = timeStamp;
        this.type = type;
    }

    public DomainEvent getDomainEvent(EventSerializer eventSerializer) {
        return eventSerializer.deserialize(serializedEvent);
    }

    public AggregateIdentifier getAggregateIdentifier() {
        return AggregateIdentifierFactory.fromString(aggregateIdentifier);
    }

    public String getType() {
        return type;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public LocalDateTime getTimeStamp() {
        return new LocalDateTime(timeStamp);
    }

    protected byte[] getSerializedEvent() {
        return serializedEvent;
    }
}