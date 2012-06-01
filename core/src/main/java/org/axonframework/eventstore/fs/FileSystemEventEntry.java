package org.axonframework.eventstore.fs;

import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SimpleSerializedObject;
import org.joda.time.DateTime;

/**
 * Represents a domain event on the file system level.
 *
 * @author Frank Versnel
 * @since 2.0
 */
public class FileSystemEventEntry implements SerializedDomainEventData {

    private String eventIdentifier;
    private String aggregateIdentifier;
    private long sequenceNumber;
    private String timeStamp;
    private String payloadType;
    private String payloadRevision;
    private byte[] metaData;
    private byte[] payload;

    /**
     * Initialize an Event entry for the given data.
     *
     * @param eventIdentifier the event identifier
     * @param aggregateIdentifier the aggregate that the event belongs to
     * @param sequenceNumber the sequence number of the event
     * @param timeStamp the timestamp of the event
     * @param payloadType the payload type of the event
     * @param payloadRevision the payload revision of the event
     * @param metaData the metadata belonging to the event
     * @param payload the payload of the event
     */
    public FileSystemEventEntry(String eventIdentifier, String aggregateIdentifier, long sequenceNumber,
                                String timeStamp, String payloadType, String payloadRevision,
                                byte[] metaData, byte[] payload) {
        this.eventIdentifier = eventIdentifier;
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
        this.timeStamp = timeStamp;
        this.payloadType = payloadType;
        this.payloadRevision = payloadRevision;
        this.metaData = metaData;
        this.payload = payload;
    }

    @Override
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public DateTime getTimestamp() {
        return new DateTime(timeStamp);
    }

    @Override
    public SerializedObject<byte[]> getPayload() {
        return new SimpleSerializedObject<byte[]>(payload, byte[].class, payloadType, payloadRevision);
    }

    @Override
    public SerializedObject<byte[]> getMetaData() {
        return new SerializedMetaData<byte[]>(metaData, byte[].class);
    }

    @Override
    public String getEventIdentifier() {
        return eventIdentifier;
    }

    @Override
    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }
}
