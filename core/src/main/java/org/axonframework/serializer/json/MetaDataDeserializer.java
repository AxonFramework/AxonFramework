package org.axonframework.serializer.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.axonframework.domain.MetaData;

import java.io.IOException;
import java.util.Map;

/**
 * JsonDeserializer implementation that deserializes MetaData instances.
 *
 * @author Allard Buijze
 * @since 2.4.2
 */
public class MetaDataDeserializer extends JsonDeserializer<MetaData> {

    @SuppressWarnings("unchecked")
    @Override
    public MetaData deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException {
        JsonDeserializer<Object> deserializer = ctxt.findRootValueDeserializer(
                ctxt.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

        return MetaData.from((Map) deserializer.deserialize(jp, ctxt));
    }
}
