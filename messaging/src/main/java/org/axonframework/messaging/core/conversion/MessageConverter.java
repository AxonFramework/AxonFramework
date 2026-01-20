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

package org.axonframework.messaging.core.conversion;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.core.Message;
import org.axonframework.conversion.Converter;

import java.lang.reflect.Type;

/**
 * A converter specific for {@link Message Messages}, acting on the payload.
 * <p>
 * This interface serves the purpose of enforcing use of the right type of converter. Implementation of this interface
 * typically delegate operations to a {@link Converter} instance.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface MessageConverter extends Converter {

    /**
     * Converts the given {@code message's} {@link Message#payload() payload} into a payload of the given
     * {@code targetType}.
     *
     * @param message    The {@code Message} to convert the {@link Message#payload() payload} for.
     * @param targetType The type to convert the {@link Message#payload() payload} into.
     * @param <M>        The type of {@code Message} to convert the payload for.
     * @param <T>        The target data type.
     * @return A converted version of the given {@code Message's} {@link Message#payload() payload} into the given
     * {@code targetType}.
     */
    @Nullable
    default <M extends Message, T> T convertPayload(@Nonnull M message, @Nonnull Class<T> targetType) {
        return convertPayload(message, (Type) targetType);
    }

    /**
     * Converts the given {@code message's} {@link Message#payload() payload} into a payload of the given
     * {@code targetType}.
     *
     * @param message    The {@code Message} to convert the {@link Message#payload() payload} for.
     * @param targetType The type to convert the {@link Message#payload() payload} into.
     * @param <M>        The type of {@code Message} to convert the payload for.
     * @param <T>        The target data type.
     * @return A converted version of the given {@code Message's} {@link Message#payload() payload} into the given
     * {@code targetType}.
     */
    @Nullable
    <M extends Message, T> T convertPayload(@Nonnull M message, @Nonnull Type targetType);

    /**
     * Converts the given {@code message's} {@link Message#payload() payload} to the given {@code targetType}, returning
     * a new {@code Message} with the converted payload.
     *
     * @param message    The {@code Message} to convert the {@link Message#payload() payload} for.
     * @param targetType The type to convert the {@link Message#payload() payload} into.
     * @param <M>        The type of {@code Message} to convert and return.
     * @param <T>        The target data type.
     * @return A new {@code Message} containing the converted version of the given {@code message's}
     * {@link Message#payload() payload} into the given {@code targetType}.
     */
    @Nonnull
    default <M extends Message, T> M convertMessage(@Nonnull M message, @Nonnull Class<T> targetType) {
        return convertMessage(message, (Type) targetType);
    }

    /**
     * Converts the given {@code message's} {@link Message#payload() payload} to the given {@code targetType}, returning
     * a new {@code Message} with the converted payload.
     *
     * @param message    The {@code Message} to convert the {@link Message#payload() payload} for.
     * @param targetType The type to convert the {@link Message#payload() payload} into.
     * @param <M>        The type of {@code Message} to convert and return.
     * @return A new {@code Message} containing the converted version of the given {@code message's}
     * {@link Message#payload() payload} into the given {@code targetType}.
     */
    @Nonnull
    <M extends Message> M convertMessage(@Nonnull M message, @Nonnull Type targetType);
}
