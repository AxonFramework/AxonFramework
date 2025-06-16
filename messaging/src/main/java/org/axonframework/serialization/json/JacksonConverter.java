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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.serialization.Converter;

import java.util.Objects;

/**
 * A {@link Converter} implementation that uses Jackson's {@link com.fasterxml.jackson.databind.ObjectMapper} to convert
 * objects into and from a JSON format.
 * <p>
 * Although the Jackson {@code Converter} requires classes to be compatible with this specific serializer, it provides
 * much more compact serialization, while still being human-readable.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 2.2.0
 */
public class JacksonConverter implements Converter {

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper
     */
    public JacksonConverter(@Nonnull ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "The ObjectMapper may not be null.");
    }

    @Override
    public boolean canConvert(@Nonnull Class<?> sourceType,
                              @Nonnull Class<?> targetType) {
        return false;
    }

    @Nullable
    @Override
    public <S, T> T convert(@Nullable S original,
                            @Nonnull Class<S> sourceType,
                            @Nonnull Class<T> targetType) {
        return null;
    }
}
