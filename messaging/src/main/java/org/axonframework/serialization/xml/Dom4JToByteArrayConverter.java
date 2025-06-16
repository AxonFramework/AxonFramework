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

package org.axonframework.serialization.xml;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.serialization.ContentTypeConverter;
import org.dom4j.Document;

import java.nio.charset.StandardCharsets;

/**
 * A {@link ContentTypeConverter} implementation that converts Dom4j {@link Document} instances to a {@code byte[]}.
 * <p>
 * The {@code Document} is written as XML {@code String}, and converted to bytes using the
 * {@link StandardCharsets#UTF_8 UTF-8 character set}.
 *
 * @author Allard Buijze
 * @since 2.0.0
 */
public class Dom4JToByteArrayConverter implements ContentTypeConverter<Document, byte[]> {

    @Override
    @Nonnull
    public Class<Document> expectedSourceType() {
        return Document.class;
    }

    @Override
    @Nonnull
    public Class<byte[]> targetType() {
        return byte[].class;
    }

    @Override
    @Nullable
    public byte[] convert(@Nullable Document input) {
        return input != null ? input.asXML().getBytes(StandardCharsets.UTF_8) : null;
    }
}
