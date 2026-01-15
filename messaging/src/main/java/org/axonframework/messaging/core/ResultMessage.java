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

package org.axonframework.messaging.core;

import jakarta.annotation.Nonnull;
import org.axonframework.common.TypeReference;
import org.axonframework.conversion.Converter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * A {@link Message} that represents a result of handling some form of a request message.
 *
 * @author Milan Savic
 * @since 4.0.0
 */
public interface ResultMessage extends Message {

    @Override
    @Nonnull
    ResultMessage withMetadata(@Nonnull Map<String, String> metadata);

    @Override
    @Nonnull
    ResultMessage andMetadata(@Nonnull Map<String, String> metadata);

    @Override
    @Nonnull
    default ResultMessage withConvertedPayload(@Nonnull Class<?> type, @Nonnull Converter converter) {
        return withConvertedPayload((Type) type, converter);
    }

    @Override
    @Nonnull
    default ResultMessage withConvertedPayload(@Nonnull TypeReference<?> type, @Nonnull Converter converter) {
        return withConvertedPayload(type.getType(), converter);
    }

    @Override
    @Nonnull
    ResultMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter);
}
