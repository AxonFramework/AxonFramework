package org.axonframework.serializer.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.serializer.SerializationException;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * @author Roald Bankras
 * @since 18/05/15
 */
public class Jsr310Deserializer<T> extends JsonDeserializer<T> {

    private final Method method;

    /**
     * @param instantType The type of object to serialize into
     * @throws org.axonframework.common.AxonConfigurationException if the given <code>instantType</code> is
     * incompatible with this serializer
     */
    public Jsr310Deserializer(Class<T> instantType) {
        try {
            this.method = instantType.getMethod("parse", CharSequence.class);
        } catch (NoSuchMethodException e) {
            throw new AxonConfigurationException(
                    "The type " + instantType.getName() + " isn't compatible with the JodaDeserializer", e);
        }
    }

    @Override
    public T deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        try {
            return (T) method.invoke(null, jp.readValueAs(String.class));
        } catch (Exception e) {
            throw new SerializationException("Unable to read instant from JSON document", e);
        }
    }

}
