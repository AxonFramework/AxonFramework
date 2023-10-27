/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serialization.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.serialization.CannotConvertBetweenTypesException;
import org.axonframework.serialization.ContentTypeConverter;

/**
 * ContentTypeConverter implementation that converts a JsonNode object into a byte[]. The byte[] will contain the UTF8
 * encoded JSON string.
 *
 * @author Allard Buijze
 * @since 2.2
 */
public class JsonNodeToByteArrayConverter implements ContentTypeConverter<JsonNode,byte[]> {

    private final ObjectMapper objectMapper;

    /**
     * Initialize the converter, using given {@code objectMapper} to convert the JSonNode into bytes. Typically,
     * this would be the objectMapper used by the Serializer that serializes objects into JsonNode.
     *
     * @param objectMapper The objectMapper to serialize the JsonNode with.
     */
    public JsonNodeToByteArrayConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Class<JsonNode> expectedSourceType() {
        return JsonNode.class;
    }

    @Override
    public Class<byte[]> targetType() {
        return byte[].class;
    }

    @Override
    public byte[] convert(JsonNode original) {
        try {
            return objectMapper.writeValueAsBytes(original);
        } catch (JsonProcessingException e) {
            throw new CannotConvertBetweenTypesException("An error occurred while converting a JsonNode to byte[]", e);
        }
    }
}
