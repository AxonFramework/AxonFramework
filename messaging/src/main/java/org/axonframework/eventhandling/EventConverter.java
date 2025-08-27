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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.serialization.Converter;

import java.lang.reflect.Type;

/**
 * A converter specific for {@link EventMessage EventMessages}, acting on the {@link EventMessage#payload() payload}.
 * <p>
 * This interface serves the purpose of enforcing use of the right type of converter. Implementation of this interface
 * typically delegate operations to a {@link org.axonframework.serialization.Converter} instance.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface EventConverter extends DescribableComponent {

    /**
     * Converts the given {@code event's} {@link EventMessage#payload() payload} into a payload of the given
     * {@code targetType}.
     *
     * @param event      The {@code EventMessage} to convert the {@link EventMessage#payload() payload} for.
     * @param targetType The type to convert the {@link EventMessage#payload() payload} into.
     * @param <E>        The type of {@code EventMessage} to convert the payload for.
     * @param <T>        The target data type.
     * @return A converted version of the given {@code EventMessage's} {@link EventMessage#payload() payload} into the
     * given {@code targetType}.
     */
    @Nullable
    default <E extends EventMessage, T> T convertPayload(@Nonnull E event, @Nonnull Class<T> targetType) {
        return convertPayload(event, (Type) targetType);
    }

    /**
     * Converts the given {@code event's} {@link EventMessage#payload() payload} into a payload of the given
     * {@code targetType}.
     *
     * @param event      The {@code EventMessage} to convert the {@link EventMessage#payload() payload} for.
     * @param targetType The type to convert the {@link EventMessage#payload() payload} into.
     * @param <E>        The type of {@code EventMessage} to convert the payload for.
     * @param <T>        The target data type.
     * @return A converted version of the given {@code EventMessage's} {@link EventMessage#payload() payload} into the
     * given {@code targetType}.
     */
    @Nullable
    <E extends EventMessage, T> T convertPayload(@Nonnull E event, @Nonnull Type targetType);

    /**
     * Converts the given {@code event's} {@link EventMessage#payload() payload} to the given {@code targetType},
     * returning a new {@code EventMessage} with the converted payload.
     *
     * @param event      The {@code EventMessage} to convert the {@link EventMessage#payload() payload} for.
     * @param targetType The type to convert the {@link EventMessage#payload() payload} into.
     * @param <E>        The type of {@code EventMessage} to convert and return.
     * @param <T>        The target data type.
     * @return A new {@code EventMessage} containing the converted version of the given {@code event's}
     * {@link EventMessage#payload() payload} into the given {@code targetType}.
     */
    @Nonnull
    default <E extends EventMessage, T> E convertEvent(@Nonnull E event, @Nonnull Class<T> targetType) {
        return convertEvent(event, (Type) targetType);
    }

    /**
     * Converts the given {@code event's} {@link EventMessage#payload() payload} to the given {@code targetType},
     * returning a new {@code EventMessage} with the converted payload.
     *
     * @param event      The {@code EventMessage} to convert the {@link EventMessage#payload() payload} for.
     * @param targetType The type to convert the {@link EventMessage#payload() payload} into.
     * @param <E>        The type of {@code EventMessage} to convert and return.
     * @return A new {@code EventMessage} containing the converted version of the given {@code event's}
     * {@link EventMessage#payload() payload} into the given {@code targetType}.
     */
    @Nonnull
    <E extends EventMessage> E convertEvent(@Nonnull E event, @Nonnull Type targetType);

    /**
     * Returns the {@link Converter} this {@code EventConverter} delegates too.
     * <p>
     * Useful to invoke {@link org.axonframework.messaging.Message#payloadAs(Type, Converter)} and
     * {@link org.axonframework.messaging.Message#withConvertedPayload(Type, Converter)}, as well as to construct other
     * instances with the exact same {@code Converter}.
     *
     * @return The {@link Converter} this {@code EventConverter} delegates too.
     */
    @Nullable
    Converter converter();
}
