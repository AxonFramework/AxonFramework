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

package org.axonframework.messaging.eventhandling.replay;

import jakarta.annotation.Nonnull;
import org.axonframework.common.TypeReference;
import org.axonframework.messaging.core.Message;
import org.axonframework.conversion.Converter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * A {@link Message} initiating the reset of an Event Handling Component.
 * <p>
 * A payload can be provided to support the reset operation handling this message.
 *
 * @author Steven van Beelen
 * @since 4.4.0
 */
public interface ResetContext extends Message {

    @Override
    @Nonnull
    ResetContext withMetadata(@Nonnull Map<String, String> metadata);

    @Override
    @Nonnull
    ResetContext andMetadata(@Nonnull Map<String, String> metadata);

    @Override
    @Nonnull
    default ResetContext withConvertedPayload(@Nonnull Class<?> type, @Nonnull Converter converter) {
        return withConvertedPayload((Type) type, converter);
    }

    @Override
    @Nonnull
    default ResetContext withConvertedPayload(@Nonnull TypeReference<?> type, @Nonnull Converter converter) {
        return withConvertedPayload(type.getType(), converter);
    }

    @Override
    @Nonnull
    ResetContext withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter);
}
