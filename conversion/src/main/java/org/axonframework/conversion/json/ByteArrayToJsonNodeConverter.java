/*
 * Copyright (c) 2010-2025. Axon Framework
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.conversion.ConversionException;
import org.axonframework.conversion.ContentTypeConverter;

import java.io.IOException;
import java.util.Objects;

/**
 * A {@link ContentTypeConverter} implementation that converts {@code byte[]} containing UTF8 encoded JSON string to a
 * {@link JsonNode}.
 *
 * @author Allard Buijze
 * @since 2.2.0
 */
public class ByteArrayToJsonNodeConverter implements ContentTypeConverter<byte[], JsonNode> {

    private final ObjectMapper objectMapper;

    /**
     * Initialize the Converter, using given {@code objectMapper} to parse the binary contents
     *
     * @param objectMapper the Jackson ObjectMapper to parse the byte array with
     */
    public ByteArrayToJsonNodeConverter(@Nonnull ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "The ObjectMapper may not be null.");
    }

    @Override
    @Nonnull
    public Class<byte[]> expectedSourceType() {
        return byte[].class;
    }

    @Override
    @Nonnull
    public Class<JsonNode> targetType() {
        return JsonNode.class;
    }

    @Override
    @Nullable
    public JsonNode convert(@Nullable byte[] input) {
        if (input == null) {
            return null;
        }

        try {
            return objectMapper.readTree(input);
        } catch (IOException e) {
            throw new ConversionException("An error occurred while converting a JsonNode to byte[].", e);
        }
    }
}
