package org.axonframework.serialization.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Map;

@JsonSerialize(using = MetaDataSerializer.class)
public abstract class MetaDataMixIn {
	@JsonCreator
	protected MetaDataMixIn(@JsonProperty("items") Map<String, Object> items){}
}
