/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.conversion.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.conversion.ConversionException;
import org.axonframework.conversion.ContentTypeConverter;

import java.util.Objects;

/**
 * A {@link ContentTypeConverter} implementation that converts a {@link JsonNode} object into a {@code byte[]}.
 * <p>
 * The {@code byte[]} will contain the UTF8 encoded JSON string.
 *
 * @author Allard Buijze
 * @since 2.2.0
 */
public class JsonNodeToByteArrayConverter implements ContentTypeConverter<JsonNode, byte[]> {

    private final ObjectMapper objectMapper;

    /**
     * Initialize the {@code JsonNodeToByteArrayConverter} using the given {@code objectMapper} to convert the
     * {@link JsonNode} into {@code byte[]}.
     *
     * @param objectMapper The object mapper to serialize the {@link JsonNode} into {@code byte[].
     */
    public JsonNodeToByteArrayConverter(@Nonnull ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "The ObjectMapper may not be null.");
    }

    @Override
    @Nonnull
    public Class<JsonNode> expectedSourceType() {
        return JsonNode.class;
    }

    @Override
    @Nonnull
    public Class<byte[]> targetType() {
        return byte[].class;
    }

    @Override
    @Nullable
    public byte[] convert(@Nullable JsonNode input) {
        try {
            return objectMapper.writeValueAsBytes(input);
        } catch (JsonProcessingException e) {
            throw new ConversionException("An error occurred while converting a JsonNode to byte[]", e);
        }
    }
}
