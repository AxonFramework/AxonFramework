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

package org.axonframework.eventhandling.replay;

import jakarta.annotation.Nonnull;
import org.axonframework.common.TypeReference;
import org.axonframework.messaging.Message;
import org.axonframework.serialization.Converter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * A {@link Message} initiating the reset of an Event Handling Component.
 * <p>
 * A payload of {@code P} can be provided to support the reset operation handling this message.
 *
 * @param <P> The type of {@link #payload() payload} contained in this {@link Message}.
 * @author Steven van Beelen
 * @since 4.4.0
 */
public interface ResetContext<P> extends Message<P> {

    @Override
    @Nonnull
    ResetContext<P> withMetaData(@Nonnull Map<String, String> metaData);

    @Override
    @Nonnull
    ResetContext<P> andMetaData(@Nonnull Map<String, String> metaData);

    @Override
    @Nonnull
    default <T> ResetContext<T> withConvertedPayload(@Nonnull Class<T> type, @Nonnull Converter converter) {
        return withConvertedPayload((Type) type, converter);
    }

    @Override
    @Nonnull
    default <T> ResetContext<T> withConvertedPayload(@Nonnull TypeReference<T> type, @Nonnull Converter converter) {
        return withConvertedPayload(type.getType(), converter);
    }

    @Override
    @Nonnull
    <T> ResetContext<T> withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter);
}
