package org.axonframework.serializer;

import org.axonframework.domain.Message;

import java.util.HashMap;
import java.util.Map;

/**
 * Holder that keeps references to serialized representations of a payload and meta data of a specific message.
 * Typically, this object should not live longer than the message object is is attached to.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SerializedObjectHolder implements SerializationAware {

    private static final ConverterFactory CONVERTER_FACTORY = new ChainingConverterFactory();

    private final Message message;
    private final Object payloadGuard = new Object();
    // guarded by "payloadGuard"
    private final Map<Serializer, SerializedObject> serializedPayload = new HashMap<Serializer, SerializedObject>();

    private final Object metaDataGuard = new Object();
    // guarded by "metaDataGuard"
    private final Map<Serializer, SerializedObject> serializedMetaData = new HashMap<Serializer, SerializedObject>();

    /**
     * Initialize the holder for the serialized representations of the payload and meta data of given
     * <code>message</code>
     *
     * @param message The message to initialize the holder for
     */
    public SerializedObjectHolder(Message message) {
        this.message = message;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> SerializedObject<T> serializePayload(Serializer serializer, Class<T> expectedRepresentation) {
        synchronized (payloadGuard) {
            SerializedObject existingForm = serializedPayload.get(serializer);
            if (existingForm == null) {
                SerializedObject<T> serialized = serializer.serialize(message.getPayload(), expectedRepresentation);
                serializedPayload.put(serializer, serialized);
                return serialized;
            } else {
                return CONVERTER_FACTORY.getConverter(existingForm.getContentType(), expectedRepresentation)
                                        .convert(existingForm);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> SerializedObject<T> serializeMetaData(Serializer serializer, Class<T> expectedRepresentation) {
        synchronized (metaDataGuard) {
            SerializedObject existingForm = serializedMetaData.get(serializer);
            if (existingForm == null) {
                SerializedObject<T> serialized = serializer.serialize(message.getMetaData(), expectedRepresentation);
                serializedMetaData.put(serializer, serialized);
                return serialized;
            } else {
                return CONVERTER_FACTORY.getConverter(existingForm.getContentType(), expectedRepresentation)
                                        .convert(existingForm);
            }
        }
    }
}
