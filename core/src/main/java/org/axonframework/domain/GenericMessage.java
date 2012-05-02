package org.axonframework.domain;

import java.util.Map;

/**
 * Generic implementation of the Message interface.
 *
 * @param <T> The type of payload contained in this message
 * @author Allard Buijze
 * @since 2.0
 */
public class GenericMessage<T> implements Message<T> {

    private static final long serialVersionUID = 4672240170797058482L;

    private final String identifier;
    private final MetaData metaData;
    // payloadType is stored separately, because of Object.getClass() performance
    private final Class payloadType;
    private final T payload;

    /**
     * Constructs a Message for the given <code>payload</code> using empty meta data.
     *
     * @param payload The payload for the message
     */
    public GenericMessage(T payload) {
        this(payload, MetaData.emptyInstance());
    }

    /**
     * Constructs a Message for the given <code>payload</code> and <code>meta data</code>.
     *
     * @param payload  The payload for the message
     * @param metaData The meta data for the message
     */
    public GenericMessage(T payload, Map<String, Object> metaData) {
        this(IdentifierFactory.getInstance().generateIdentifier(), payload, MetaData.from(metaData));
    }

    /**
     * Constructor to reconstruct a Message using existing data.
     *
     * @param identifier The identifier of the Message
     * @param payload    The payload of the message
     * @param metaData   The meta data of the message
     */
    public GenericMessage(String identifier, T payload, Map<String, Object> metaData) {
        this.identifier = identifier;
        this.metaData = MetaData.from(metaData);
        this.payload = payload;
        this.payloadType = payload.getClass();
    }

    private GenericMessage(GenericMessage<T> original, Map<String, Object> metaData) {
        this.identifier = original.getIdentifier();
        this.payload = original.getPayload();
        this.payloadType = payload.getClass();
        this.metaData = MetaData.from(metaData);
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public MetaData getMetaData() {
        return metaData;
    }

    @Override
    public T getPayload() {
        return payload;
    }

    @Override
    public Class getPayloadType() {
        return payloadType;
    }

    @Override
    public GenericMessage<T> withMetaData(Map<String, Object> newMetaData) {
        if (this.metaData.equals(newMetaData)) {
            return this;
        }
        return new GenericMessage<T>(this, newMetaData);
    }

    @Override
    public GenericMessage<T> andMetaData(Map<String, Object> additionalMetaData) {
        if (additionalMetaData.isEmpty()) {
            return this;
        }
        return new GenericMessage<T>(this, this.metaData.mergedWith(additionalMetaData));
    }
}
