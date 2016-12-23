package org.axonframework.serialization.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import org.axonframework.messaging.MetaData;

import java.io.IOException;
import java.util.HashMap;

/**
 * JsonSerializer implementation that serializes MetaData instances.
 */
public class MetaDataSerializer extends JsonSerializer<MetaData> {
	@Override
	public void serializeWithType(MetaData metaData, JsonGenerator jsonGenerator, SerializerProvider serializerProvider, TypeSerializer typeSerializer) throws IOException, JsonProcessingException {
		typeSerializer.writeTypePrefixForObject(metaData, jsonGenerator, MetaData.class);
		HashMap<String, Object> hashMap = new HashMap<>(metaData);
		jsonGenerator.writeObjectField("items", hashMap);
		typeSerializer.writeTypeSuffixForObject(metaData, jsonGenerator);
	}

	@Override
	public void serialize(MetaData metaData, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
		HashMap<String, Object> hashMap = new HashMap<>(metaData);
		jsonGenerator.writeObject(hashMap);
	}
}
