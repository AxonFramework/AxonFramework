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
import nu.xom.Document;
import org.axonframework.serialization.ContentTypeConverter;
import org.axonframework.serialization.Converter;

/**
 * A {@link ContentTypeConverter} implementation that converts XOM {@link Document} instances to {@code Strings}.
 * <p>
 * The {@code Document} is written as XML {@code String}.
 *
 * @author Jochen Munz
 * @since 2.2.0
 * @deprecated In favor of an XML-based Jackson-specific {@link Converter} implementation.
 */
@Deprecated(forRemoval = true, since = "5.0.0")
public class XomToStringConverter implements ContentTypeConverter<Document, String> {

    @Override
    @Nonnull
    public Class<Document> expectedSourceType() {
        return Document.class;
    }

    @Override
    @Nonnull
    public Class<String> targetType() {
        return String.class;
    }

    @Override
    @Nullable
    public String convert(@Nullable Document input) {
        return input != null ? input.toXML() : null;
    }
}