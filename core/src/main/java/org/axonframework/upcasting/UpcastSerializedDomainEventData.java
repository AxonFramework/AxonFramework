package org.axonframework.upcasting;

import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedObject;
import org.joda.time.DateTime;

/**
 * SerializedDomainEventData implementation that can be used to duplicate existing SerializedDomainEventData instances
 * after upcasting a payload.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class UpcastSerializedDomainEventData<T> implements SerializedDomainEventData<T> {

    private final SerializedDomainEventData<T> original;
    private final Object identifier;
    private final SerializedObject<T> upcastPayload;

    /**
     * Reconstruct the given <code>original</code> SerializedDomainEventData replacing the aggregateIdentifier with
     * given <code>aggregateIdentifier</code> and payload with <code>upcastPayload</code>. Typically, for each payload
     * instance returned after an upcast, a single UpcastSerializedDomainEventData instance is to be created.
     *
     * @param original            The original SerializedDomainEventData instance
     * @param aggregateIdentifier The aggregate identifier instance
     * @param upcastPayload       The replacement payload
     */
    public UpcastSerializedDomainEventData(SerializedDomainEventData<T> original, Object aggregateIdentifier,
                                           SerializedObject<T> upcastPayload) {
        this.original = original;
        this.identifier = aggregateIdentifier;
        this.upcastPayload = upcastPayload;
    }

    @Override
    public String getEventIdentifier() {
        return original.getEventIdentifier();
    }

    @Override
    public Object getAggregateIdentifier() {
        return identifier;
    }

    @Override
    public long getSequenceNumber() {
        return original.getSequenceNumber();
    }

    @Override
    public DateTime getTimestamp() {
        return original.getTimestamp();
    }

    @Override
    public SerializedObject<T> getMetaData() {
        return original.getMetaData();
    }

    public SerializedObject<T> getPayload() {
        return upcastPayload;
    }
}
