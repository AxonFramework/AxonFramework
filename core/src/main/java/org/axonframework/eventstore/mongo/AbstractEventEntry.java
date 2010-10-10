package org.axonframework.eventstore.mongo;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.AggregateIdentifierFactory;
import org.axonframework.domain.DomainEvent;
import org.axonframework.eventstore.EventSerializer;
import org.joda.time.LocalDateTime;

import java.nio.charset.Charset;

/**
 * Data needed by different types of event logs.
 *
 * @author Allard Buijze
 * @author Jettro Coenradie
 * @since 0.7
 */
public class AbstractEventEntry {
    protected static final Charset UTF8 = Charset.forName("UTF-8");
    private String aggregateIdentifier;
    private long sequenceNumber;
    private String timeStamp;
    private String type;
    private String serializedEvent;

    protected AbstractEventEntry(String type, DomainEvent event, EventSerializer eventSerializer) {
        this.type = type;
        this.aggregateIdentifier = event.getAggregateIdentifier().toString();
        this.sequenceNumber = event.getSequenceNumber();
        this.serializedEvent = new String(eventSerializer.serialize(event),UTF8);
        this.timeStamp = event.getTimestamp().toString();
    }

    protected AbstractEventEntry(String aggregateIdentifier, long sequenceNumber, String serializedEvent, String timeStamp, String type) {
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
        this.serializedEvent = serializedEvent;
        this.timeStamp = timeStamp;
        this.type = type;
    }

    public DomainEvent getDomainEvent(EventSerializer eventSerializer) {
        return eventSerializer.deserialize(serializedEvent.getBytes(UTF8));
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

    protected String getSerializedEvent() {
        return serializedEvent;
    }
}