package org.axonframework.serializer;

import org.axonframework.domain.DomainEventMessage;

import java.util.Map;

/**
 * Wrapper around a DomainEventMessage that adds "Serialization Awareness" to the message it wraps. This implementation
 * ensures that, when the payload or meta data is being serialized more than once using the same serializer, only a
 * single serialization will actually occur. Subsequent invocations will return the same <code>SerializedObject</code>
 * instance as the first.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SerializationAwareDomainEventMessage<T> extends SerializationAwareEventMessage<T>
        implements SerializationAware, DomainEventMessage<T> {

    private static final long serialVersionUID = 6669217743673381890L;

    private final DomainEventMessage<T> domainEventMessage;

    /**
     * Wrap the given <code>message</code> to make it SerializationAware. The returning object can be safely cast to
     * {@link SerializationAware}. If the given <code>message</code> already implements
     * <code>SerializationAware</code>, it is returned as-is. It is therefore not safe to assume the returning message
     * is an instance of <code>SerializationAwareDomainEventMessage</code>.
     *
     * @param message The message to wrap
     * @param <T>     The payload type of the message
     * @return a serialization aware version of the given message
     */
    public static <T> DomainEventMessage<T> wrap(DomainEventMessage<T> message) {
        if (message instanceof SerializationAware) {
            return message;
        }
        return new SerializationAwareDomainEventMessage<T>(message);
    }

    /**
     * Initialize a new wrapper for the given <code>message</code>.
     *
     * @param message The message to wrap
     */
    protected SerializationAwareDomainEventMessage(DomainEventMessage<T> message) {
        super(message);
        this.domainEventMessage = message;
    }

    @Override
    public long getSequenceNumber() {
        return domainEventMessage.getSequenceNumber();
    }

    @Override
    public Object getAggregateIdentifier() {
        return domainEventMessage.getAggregateIdentifier();
    }

    @Override
    public DomainEventMessage<T> withMetaData(Map<String, ?> metaData) {
        final DomainEventMessage<T> newMessage = domainEventMessage.withMetaData(metaData);
        if (domainEventMessage == newMessage) { // NOSONAR - Equal instance check on purpose
            return this;
        }
        return new SerializationAwareDomainEventMessage<T>(newMessage);
    }

    @Override
    public DomainEventMessage<T> andMetaData(Map<String, ?> metaData) {
        final DomainEventMessage<T> newMessage = domainEventMessage.andMetaData(metaData);
        if (domainEventMessage == newMessage) { // NOSONAR - Equal instance check on purpose
            return this;
        }
        return new SerializationAwareDomainEventMessage<T>(newMessage);
    }

    /**
     * Replacement function for Java Serialization API. When this object is serialized, it is replaced by the
     * implementation it wraps.
     *
     * @return the DomainEventMessage wrapped by this message
     */
    protected Object writeReplace() {
        return domainEventMessage;
    }
}
