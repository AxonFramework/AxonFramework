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

package org.axonframework.serialization.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.serialization.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Converter implementation using Jackson for object serialization and deserialization.
 * <p>
 * This converter provides robust and maintainable format handling for event payloads and other objects,
 * using Jackson for transformation between object and wire formats (such as JSON in String or byte[] form).
 * <p>
 * Intended for use with Axon Framework event storage and messaging, ensuring consistent payload
 * transformation between object and wire formats. Users may provide a custom-configured {@link ObjectMapper}
 * or use the default.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 * TODO #3102 - Validate this Converter as part of #3102
 */
public final class JacksonConverter implements Converter {

    private static final Logger logger = LoggerFactory.getLogger(JacksonConverter.class);

    private final ObjectMapper objectMapper;

    /**
     * Constructs a JacksonFacultyEventConverter with a default ObjectMapper.
     * The default mapper does not register any custom serializers or deserializers.
     */
    public JacksonConverter() {
        this(new ObjectMapper().findAndRegisterModules());
    }

    /**
     * Constructs a JacksonFacultyEventConverter with a user-provided ObjectMapper.
     *
     * @param objectMapper The ObjectMapper to use for serialization/deserialization.
     */
    public JacksonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean canConvert(@Nonnull Class<?> sourceType, @Nonnull Class<?> targetType) {
        return sourceType.equals(targetType) || canSerialize(targetType) || canDeserialize(sourceType);
    }

    private static boolean canSerialize(Class<?> targetType) {
        return byte[].class.isAssignableFrom(targetType) || String.class.isAssignableFrom(targetType);
    }

    private static boolean canDeserialize(Class<?> sourceType) {
        return byte[].class.isAssignableFrom(sourceType) || String.class.isAssignableFrom(sourceType);
    }

    @Override
    @Nullable
    public <S, T> T convert(@Nullable S input, @Nonnull Class<S> sourceType, @Nonnull Class<T> targetType) {
        if (input == null) {
            return null;
        }

        if (sourceType.equals(targetType)) {
            return targetType.cast(input);
        }

        try {
            return performConversion(input, sourceType, targetType);
        } catch (JsonProcessingException e) {
            var errorMessage = """
                Failed to convert between %s and %s: %s
                """.formatted(sourceType.getSimpleName(), targetType.getSimpleName(), e.getMessage());
            logger.error(errorMessage, e);
            throw new ConversionException(errorMessage, e);
        }
    }

    @SuppressWarnings("unchecked")
    private <S, T> T performConversion(S input, Class<S> sourceType, Class<T> targetType)
            throws JsonProcessingException {

        if (input instanceof byte[] && targetType.equals(String.class)) {
            return objectMapper.readValue(new String((byte[]) input), targetType);
        }

        // Handle serialization: event -> byte[] or String
        if (canSerialize(targetType)) {
            var json = objectMapper.writeValueAsString(input);
            return switch (targetType.getName()) {
                case "[B" -> (T) json.getBytes(StandardCharsets.UTF_8); // byte[]
                case "java.lang.String" -> (T) json;
                default -> throw new IllegalArgumentException("Unsupported target type: " + targetType);
            };
        }

        // Handle deserialization: byte[] or String -> event
        var jsonInput = switch (input) {
            case byte[] bytes -> new String(bytes, StandardCharsets.UTF_8);
            case String str -> str;
            default -> throw new IllegalArgumentException("Unsupported source type: " + sourceType);
        };

        return objectMapper.readValue(jsonInput, targetType);
    }

    /**
     * Custom exception for conversion failures.
     */
    public static final class ConversionException extends RuntimeException {

        /**
         * Constructs a new ConversionException with the specified detail message.
         * @param message The detail message.
         * @param cause The cause of the exception.
         */
        public ConversionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
