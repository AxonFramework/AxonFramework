package org.axonframework.serializer.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.AssociationValues;
import org.axonframework.saga.annotation.AssociationValuesImpl;

import java.io.IOException;

/**
 * Created by graham brooks on 6/11/15.
 */
public class AssociationValuesDeserializer extends JsonDeserializer<AssociationValues> {
    @Override
    public AssociationValues deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        //AssociationValue value =  deserializationContext.getTypeFactory().constructMapType(Set.class, String.class, String.class));
        JsonDeserializer<Object> deserializer = deserializationContext.findRootValueDeserializer(
                deserializationContext.getTypeFactory().constructArrayType(AssociationValue.class));

        Object values = deserializer.deserialize(jsonParser, deserializationContext);

        AssociationValues result = new AssociationValuesImpl();
        for (AssociationValue value : (AssociationValue[]) values) result.add(value);

        return result;
    }
}
