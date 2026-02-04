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

package org.axonframework.conversion.converter;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.conversion.ContentTypeConverter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * A {@link ContentTypeConverter} implementation that converts {@code byte[]} into an {@link InputStream}.
 * <p>
 * More specifically, it returns an {@link ByteArrayInputStream} with the underlying {@code byte[]} is backing data.
 *
 * @author Allard Buijze
 * @since 2.0.0
 */
public class ByteArrayToInputStreamConverter implements ContentTypeConverter<byte[], InputStream> {

    @Override
    @Nonnull
    public Class<byte[]> expectedSourceType() {
        return byte[].class;
    }

    @Override
    @Nonnull
    public Class<InputStream> targetType() {
        return InputStream.class;
    }

    @Override
    @Nullable
    public InputStream convert(@Nullable byte[] input) {
        return input != null ? new ByteArrayInputStream(input) : null;
    }
}
