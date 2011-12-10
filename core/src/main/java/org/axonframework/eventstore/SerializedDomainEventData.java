package org.axonframework.eventstore;

import org.axonframework.serializer.SerializedObject;
import org.joda.time.DateTime;

/**
 * Interface describing the properties of serialized Domain Event Messages. Event Store implementations should have
 * their storage entries implement this interface.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface SerializedDomainEventData {

    /**
     * Returns the identifier of the serialized event.
     *
     * @return the identifier of the serialized event
     */
    String getEventIdentifier();

    /**
     * Returns the Identifier of the Aggregate to which the Event was applied.
     *
     * @return the Identifier of the Aggregate to which the Event was applied
     */
    Object getAggregateIdentifier();

    /**
     * Returns the sequence number of the event in the aggregate.
     *
     * @return the sequence number of the event in the aggregate
     */
    long getSequenceNumber();

    /**
     * Returns the timestamp at which the event was first created.
     *
     * @return the timestamp at which the event was first created
     */
    DateTime getTimestamp();

    /**
     * Returns the serialized data of the MetaData of the serialized Event.
     *
     * @return the serialized data of the MetaData of the serialized Event
     */
    SerializedObject getMetaData();

    /**
     * Returns the serialized data of the Event Message's payload.
     *
     * @return the serialized data of the Event Message's payload
     */
    SerializedObject getPayload();
}
