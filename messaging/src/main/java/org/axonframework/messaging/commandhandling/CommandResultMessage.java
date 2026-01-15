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

package org.axonframework.messaging.commandhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.TypeReference;
import org.axonframework.messaging.core.ResultMessage;
import org.axonframework.conversion.Converter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * A {@link ResultMessage} that represents a result from handling a {@link CommandMessage}.
 *
 * @author Milan Savic
 * @since 4.0.0
 */
public interface CommandResultMessage extends ResultMessage {

    @Override
    @Nonnull
    CommandResultMessage withMetadata(@Nonnull Map<String, String> metadata);

    @Override
    @Nonnull
    CommandResultMessage andMetadata(@Nonnull Map<String, String> metadata);

    @Override
    @Nonnull
    default CommandResultMessage withConvertedPayload(@Nonnull Class<?> type, @Nonnull Converter converter) {
        return withConvertedPayload((Type) type, converter);
    }

    @Override
    @Nonnull
    default CommandResultMessage withConvertedPayload(@Nonnull TypeReference<?> type,
                                                      @Nonnull Converter converter) {
        return withConvertedPayload(type.getType(), converter);
    }

    @Override
    @Nonnull
    CommandResultMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter);
}
