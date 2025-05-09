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

package org.axonframework.axonserver.connector.event;

import org.axonframework.serialization.Converter;

import java.nio.charset.StandardCharsets;

/**
 * Simple test implementation of the {@link Converter} expecting {@code byte[]}.
 *
 * @author Allard Buijze
 */
public class TestConverter implements Converter {

    @Override
    public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
        return byte[].class.isAssignableFrom(targetType) || String.class.isAssignableFrom(sourceType);
    }

    @Override
    public <T> T convert(Object original, Class<?> sourceType, Class<T> targetType) {
        if (byte[].class.isAssignableFrom(targetType)) {
            return (T) original.toString().getBytes(StandardCharsets.UTF_8);
        } else if (String.class.isAssignableFrom(targetType)) {
            //noinspection unchecked
            return (T) original.toString();
        } else {
            throw new IllegalArgumentException("Only supports byte[] and String.");
        }
    }
}
