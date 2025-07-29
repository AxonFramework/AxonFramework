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
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.serialization.ChainingContentTypeConverter;
import org.axonframework.serialization.ConversionException;
import org.axonframework.serialization.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * A {@link Converter} implementation that uses Jackson's {@link com.fasterxml.jackson.databind.ObjectMapper} to convert
 * objects into and from a JSON format.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Steven van Beelen
 * @since 2.2.0
 */
public class JacksonConverter implements Converter {

    private static final Logger logger = LoggerFactory.getLogger(JacksonConverter.class);

    private final ObjectMapper objectMapper;
    private final ChainingContentTypeConverter converter;

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
        this.converter = new ChainingContentTypeConverter();
        this.converter.registerConverter(new JsonNodeToByteArrayConverter(this.objectMapper));
        this.converter.registerConverter(new ByteArrayToJsonNodeConverter(this.objectMapper));
        this.converter.registerConverter(new JsonNodeToObjectNodeConverter());
        this.converter.registerConverter(new ObjectNodeToJsonNodeConverter());
    }

    @Override
    public boolean canConvert(@Nonnull Type sourceType,
                              @Nonnull Type targetType) {
        if (logger.isTraceEnabled()) {
            logger.trace("Validating if we can convert from source type [{}] to target type [{}].",
                         sourceType, targetType);
        }
        return sourceType.equals(targetType)
                || converter.canConvert(sourceType, targetType)
                || converter.canConvert(sourceType, byte[].class)
                || converter.canConvert(byte[].class, targetType);
    }

    @Nullable
    @Override
    public <T> T convert(@Nullable Object input,
                         @Nonnull Type targetType) {
        if (input == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("Input to convert is null, so returning null immediately.");
            }
            return null;
        }

        Class<?> sourceType = input.getClass();
        if (sourceType.equals(targetType)) {
            if (logger.isTraceEnabled()) {
                logger.trace("Casting given input since source and target type are identical.");
            }
            //noinspection unchecked
            return (T) input;
        }

        try {
            JavaType targetJavaType = objectMapper.constructType(targetType);
            if (converter.canConvert(sourceType, targetJavaType.getRawClass())) {
                if (logger.isTraceEnabled()) {
                    logger.trace(
                            "Converter [{}] will do the entire conversion from source [{}] to target [{}] for [{}].",
                            converter, sourceType, targetType, input
                    );
                }
                //noinspection unchecked
                return (T) converter.convert(input, targetJavaType.getRawClass());
            } else if (converter.canConvert(sourceType, byte[].class)) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Converts input [{}] to byte[] before reading it into [{}].", input, targetJavaType);
                }
                return objectMapper.readValue(converter.convert(input, byte[].class), targetJavaType);
            } else if (converter.canConvert(byte[].class, targetJavaType.getRawClass())) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Writes input [{}] as a byte[] before converting to [{}].", input, targetJavaType);
                }
                // Converting to byte[] from some input type.
                //noinspection unchecked
                return (T) converter.convert(objectMapper.writeValueAsBytes(input), targetJavaType.getRawClass());
            } else {
                if (logger.isTraceEnabled()) {
                    logger.trace("ObjectMapper [{}] will convert input [{}] to target type [{}].",
                                 objectMapper, input, targetJavaType);
                }
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
