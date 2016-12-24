/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serialization.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import org.axonframework.messaging.MetaData;

import java.io.IOException;
import java.util.HashMap;

/**
 * JsonSerializer implementation that serializes MetaData instances.
 */
class MetaDataSerializer extends JsonSerializer<MetaData> {
	@Override
	public void serializeWithType(MetaData value, JsonGenerator jgen, SerializerProvider provider, TypeSerializer typeSer) throws IOException {
		typeSer.writeTypePrefixForScalar(value, jgen, MetaData.class);
		serialize(value, jgen, provider);
		typeSer.writeTypeSuffixForScalar(value, jgen);
	}

	@Override
	public void serialize(MetaData value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
		jgen.writeObject(new HashMap<>(value));
	}
}
