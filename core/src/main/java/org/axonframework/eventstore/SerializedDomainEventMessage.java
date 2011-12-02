package org.axonframework.eventstore;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.joda.time.DateTime;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;

/**
 * DomainEventMessage implementation that is optimized to cope with serialized Payload and MetaData. The Payload and
 * MetaData will only be deserialized when requested. This means that loaded event for which there is no handler will
 * never be deserialized.
 * <p/>
 * This implementation is Serializable as per Java specification. Both MetaData and Payload are deserialized prior to
 * being written to the OutputStream.
 *
 * @param <T> The type of payload contained in this message
 * @author Allard Buijze
 * @since 2.0
 */
public class SerializedDomainEventMessage<T> implements DomainEventMessage<T> {

    private static final long serialVersionUID = -9177715264819287285L;

    private final long sequenceNumber;
    private final AggregateIdentifier aggregateIdentifier;
    private final String eventIdentifier;
    private final DateTime timestamp;
    private volatile MetaData metaData;
    private volatile T payload;
    private volatile Class payloadType;
    private transient final SerializedObject serializedMetaData;
    private transient final SerializedObject serializedPayload;
    private transient final Serializer payloadSerializer;
    private transient final Serializer metaDataSerializer;

    /**
     * Creates a new instance with given serialized <code>data</code>, with data to be deserialized with given
     * <code>payloadSerializer</code> and <code>metaDataSerializer</code>.
     *
     * @param data               The serialized data for this EventMessage
     * @param payloadSerializer  The serializer to deserialize the payload data with
     * @param metaDataSerializer The serializer to deserialize meta data with
     */
    public SerializedDomainEventMessage(SerializedDomainEventData data, Serializer payloadSerializer,
                                        Serializer metaDataSerializer) {
        this(data.getEventIdentifier(), data.getAggregateIdentifier(), data.getSequenceNumber(), data.getTimestamp(),
             data.getPayload(), data.getMetaData(),
             payloadSerializer, metaDataSerializer);
    }

    /**
     * Creates a new instance with given event details,to be deserialized with given
     * <code>payloadSerializer</code> and <code>metaDataSerializer</code>.
     *
     * @param eventIdentifier     The identifier of the EventMessage
     * @param aggregateIdentifier The identifier of the Aggregate this message originates from
     * @param sequenceNumber      The sequence number that represents the order in which the message is generated
     * @param timestamp           The timestamp of (original) message creation
     * @param serializedPayload   The serialized payload of the message
     * @param serializedMetaData  The serialized meta data of the message
     * @param payloadSerializer   The serializer to deserialize the payload data with
     * @param metaDataSerializer  The serializer to deserialize meta data with
     */
    public SerializedDomainEventMessage(String eventIdentifier, AggregateIdentifier aggregateIdentifier,
                                        long sequenceNumber,
                                        DateTime timestamp, SerializedObject serializedPayload,
                                        SerializedObject serializedMetaData, Serializer payloadSerializer,
                                        Serializer metaDataSerializer) {
        this.sequenceNumber = sequenceNumber;
        this.aggregateIdentifier = aggregateIdentifier;
        this.eventIdentifier = eventIdentifier;
        this.timestamp = timestamp;
        this.serializedMetaData = serializedMetaData;
        this.serializedPayload = serializedPayload;
        this.payloadSerializer = payloadSerializer;
        this.metaDataSerializer = metaDataSerializer;
    }

    private SerializedDomainEventMessage(SerializedDomainEventMessage<T> original, Map<String, Object> metaData) {
        this.metaData = MetaData.from(metaData);
        this.payload = original.payload;
        this.aggregateIdentifier = original.getAggregateIdentifier();
        this.sequenceNumber = original.getSequenceNumber();
        this.eventIdentifier = original.getEventIdentifier();
        this.timestamp = original.getTimestamp();
        this.serializedMetaData = null;
        this.metaDataSerializer = null;
        this.serializedPayload = original.serializedPayload;
        this.payloadSerializer = original.payloadSerializer;
    }

    @Override
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public AggregateIdentifier getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    @Override
    public String getEventIdentifier() {
        return eventIdentifier;
    }

    @Override
    public DateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public MetaData getMetaData() {
        ensureMetaDataDeserialized();
        return metaData;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public T getPayload() {
        ensurePayloadDeserialized();
        return payload;
    }

    @Override
    public Class getPayloadType() {
        ensurePayloadTypeDeserialized();
        return payloadType;
    }

    @Override
    public DomainEventMessage<T> withMetaData(Map<String, Object> metaData) {
        if (payload != null) {
            return new GenericDomainEventMessage<T>(aggregateIdentifier, sequenceNumber, payload, metaData);
        } else {
            return new SerializedDomainEventMessage<T>(this, metaData);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This method will force the MetaData to be deserialized if not already done.
     */
    @Override
    public DomainEventMessage<T> andMetaData(Map<String, Object> additionalMetaData) {
        MetaData newMetaData = getMetaData().mergedWith(additionalMetaData);
        return withMetaData(newMetaData);
    }

    private void ensurePayloadTypeDeserialized() {
        if (payloadType == null) {
            payloadType = payloadSerializer.classForType(serializedPayload.getType());
        }
    }

    private void ensureMetaDataDeserialized() {
        if (metaData == null && (serializedMetaData == null || metaDataSerializer == null)) {
            metaData = MetaData.emptyInstance();
        } else if (metaData == null) {
            metaData = (MetaData) metaDataSerializer.deserialize(serializedMetaData);
        }
    }

    @SuppressWarnings({"unchecked"})
    private void ensurePayloadDeserialized() {
        if (payload == null) {
            payload = (T) payloadSerializer.deserialize(serializedPayload);
        }
    }

    private void writeObject(ObjectOutputStream outputStream) throws IOException {
        ensureMetaDataDeserialized();
        ensurePayloadDeserialized();
        outputStream.defaultWriteObject();
    }
}
