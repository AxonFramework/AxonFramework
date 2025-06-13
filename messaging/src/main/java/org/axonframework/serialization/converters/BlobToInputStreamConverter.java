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

package org.axonframework.serialization.converters;

import jakarta.annotation.Nonnull;
import org.axonframework.serialization.CannotConvertBetweenTypesException;
import org.axonframework.serialization.ContentTypeConverter;

import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;

/**
 * A {@link ContentTypeConverter} implementation that converts {@link Blob} into an {@link InputStream}.
 *
 * @author Allard Buijze
 * @since 2.3.0
 */
public class BlobToInputStreamConverter implements ContentTypeConverter<Blob, InputStream> {

    @Override
    @Nonnull
    public Class<Blob> expectedSourceType() {
        return Blob.class;
    }

    @Override
    @Nonnull
    public Class<InputStream> targetType() {
        return InputStream.class;
    }

    @Override
    @Nonnull
    public InputStream convert(@Nonnull Blob original) {
        try {
            return original.getBinaryStream();
        } catch (SQLException e) {
            throw new CannotConvertBetweenTypesException("Error while attempting to read data from Blob.", e);
        }
    }
}
