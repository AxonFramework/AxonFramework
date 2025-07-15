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

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.serialization.ChainingConverter;
import org.axonframework.serialization.ConversionException;
import org.axonframework.serialization.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * A {@link Converter} implementation that uses Jackson's {@link com.fasterxml.jackson.databind.ObjectMapper} to convert
 * objects into and from a JSON format.
 * <p>
 * Although the Jackson {@code Converter} requires classes to be compatible with this specific serializer, it provides
 * much more compact serialization, while still being human-readable.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Steven van Beelen
 * @since 2.2.0
 */
public class JacksonConverter implements Converter {

    private static final Logger logger = LoggerFactory.getLogger(JacksonConverter.class);

    private final ObjectMapper objectMapper;
    private final ChainingConverter converter;

    /**
     * Constructs a {@code JacksonConverter} with a default {@link ObjectMapper} that
     * {@link ObjectMapper#findAndRegisterModules() finds and registers known modules}.
     */
    public JacksonConverter() {
        this(new ObjectMapper().findAndRegisterModules());
    }

    /**
     * Constructs a {@code JacksonConverter} with the given {@code objectMapper}.
     *
     * @param objectMapper The mapper used to convert objects into and from a JSON format.
     */
    public JacksonConverter(@Nonnull ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "The ObjectMapper may not be null.");
        this.objectMapper.registerModule(new JavaTimeModule());
        // TODO The Converter used to be configurable for the JacksonSerializer. I don't think we need that anymore. Thoughts?
        this.converter = new ChainingConverter();
        this.converter.registerConverter(new JsonNodeToByteArrayConverter(this.objectMapper));
        this.converter.registerConverter(new ByteArrayToJsonNodeConverter(this.objectMapper));
        this.converter.registerConverter(new JsonNodeToObjectNodeConverter());
        this.converter.registerConverter(new ObjectNodeToJsonNodeConverter());
    }

    @Override
    public boolean canConvert(@Nonnull Class<?> sourceType,
                              @Nonnull Class<?> targetType) {
        return sourceType.equals(targetType)
                || converter.canConvert(sourceType, targetType)
                || converter.canConvert(sourceType, byte[].class)
                || converter.canConvert(byte[].class, targetType);
    }

    @Nullable
    @Override
    public <S, T> T convert(@Nullable S input,
                            @Nonnull Class<S> sourceType,
                            @Nonnull Class<T> targetType) {
        if (input == null) {
            return null;
        }

        if (sourceType.equals(targetType)) {
            return targetType.cast(input);
        }

        try {
            JavaType targetJavaType = objectMapper.constructType(targetType);
            if (converter.canConvert(sourceType, targetJavaType.getRawClass())) {
                // Converter has got this entirely.
                //noinspection unchecked
                return (T) converter.convert(input, targetJavaType.getRawClass());
            } else if (converter.canConvert(sourceType, byte[].class)) {
                // Converting from source byte[] to requested target type.
                return objectMapper.readValue(converter.convert(input, byte[].class), targetJavaType);
            } else if (converter.canConvert(targetJavaType.getRawClass(), byte[].class)) {
                // Converting to byte[] from some input type.
                //noinspection unchecked
                return (T) converter.convert(objectMapper.writeValueAsBytes(input), targetJavaType.getRawClass());
            } else {
                // Unsure, let's see of the ObjectMapper can do this itself.
                return objectMapper.convertValue(input, targetJavaType);
            }
        } catch (IOException e) {
            throw new ConversionException(
                    "Exception when trying to convert object of type '" + sourceType.getTypeName() + "' to '"
                            + targetType.getTypeName() + "'", e
            );
        }
    }
}
