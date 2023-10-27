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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.serialization.CannotConvertBetweenTypesException;
import org.axonframework.serialization.ContentTypeConverter;

import java.io.IOException;

/**
 * ContentTypeConverter implementation that converts byte[] containing UTF8 encoded JSON string to a Jackson JsonNode.
 *
 * @author Allard Buijze
 * @since 2.2
 */
public class ByteArrayToJsonNodeConverter implements ContentTypeConverter<byte[],JsonNode> {

    private final ObjectMapper objectMapper;

    /**
     * Initialize the Converter, using given {@code objectMapper} to parse the binary contents
     *
     * @param objectMapper the Jackson ObjectMapper to parse the byte array with
     */
    public ByteArrayToJsonNodeConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Class<byte[]> expectedSourceType() {
        return byte[].class;
    }

    @Override
    public Class<JsonNode> targetType() {
        return JsonNode.class;
    }

    @Override
    public JsonNode convert(byte[] original) {
        try {
            return objectMapper.readTree(original);
        } catch (IOException e) {
            throw new CannotConvertBetweenTypesException("An error occurred while converting a JsonNode to byte[]", e);
        }
    }
}
