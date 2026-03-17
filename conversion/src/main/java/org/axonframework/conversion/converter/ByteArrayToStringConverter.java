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

package org.axonframework.conversion.converter;

import org.jspecify.annotations.Nullable;
import org.axonframework.conversion.ContentTypeConverter;

import java.nio.charset.StandardCharsets;

/**
 * A {@link ContentTypeConverter} implementation that converts {@code byte[]} into {@code String}.
 * <p>
 * Conversion is done using the {@link StandardCharsets#UTF_8 UTF-8 character set}.
 *
 * @author Allard Buijze
 * @since 2.0.0
 */
public class ByteArrayToStringConverter implements ContentTypeConverter<byte[], String> {

    @Override
    public Class<byte[]> expectedSourceType() {
        return byte[].class;
    }

    @Override
    public Class<String> targetType() {
        return String.class;
    }

    @Override
    public String convert(byte @Nullable[] input) {
        return input != null ? new String(input, StandardCharsets.UTF_8) : null;
    }
}
