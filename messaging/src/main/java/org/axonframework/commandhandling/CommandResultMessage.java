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

package org.axonframework.commandhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.TypeReference;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.serialization.Converter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * A {@link ResultMessage} that represents a result from handling a {@link CommandMessage}.
 *
 * @param <R> The type of {@link #payload() result} contained in this {@link CommandResultMessage}.
 * @author Milan Savic
 * @since 4.0.0
 */
public interface CommandResultMessage<R> extends ResultMessage<R> {

    @Override
    CommandResultMessage<R> withMetaData(@Nonnull Map<String, String> metaData);

    @Override
    CommandResultMessage<R> andMetaData(@Nonnull Map<String, String> metaData);

    @Override
    default <T> CommandResultMessage<T> withConvertedPayload(@Nonnull Class<T> type, @Nonnull Converter converter) {
        return withConvertedPayload((Type) type, converter);
    }

    @Override
    default <T> CommandResultMessage<T> withConvertedPayload(@Nonnull TypeReference<T> type,
                                                             @Nonnull Converter converter) {
        return withConvertedPayload(type.getType(), converter);
    }

    @Override
    <T> CommandResultMessage<T> withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter);
}
