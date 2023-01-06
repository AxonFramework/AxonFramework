/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.javax.eventhandling.deadletter.jpa;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.serialization.Serializer;

/**
 * Converter that can convert a {@link EventMessage} to a {@link DeadLetterEventEntry} and vice versa.
 *
 * @param <M> The type of the event message this converter will convert.
 * @author Mitchell Herrijgers
 * @since 4.6.0
 * @deprecated in favor of using {@link org.axonframework.eventhandling.deadletter.jpa.DeadLetterJpaConverter} which
 * moved to jakarta.
 */
@Deprecated
public interface DeadLetterJpaConverter<M extends EventMessage<?>> {

    /**
     * Converts an {@link EventMessage} implementation to a {@link DeadLetterEventEntry}.
     *
     * @param message           The message to convert.
     * @param eventSerializer   The {@link Serializer} for serialization of payload and metadata.
     * @param genericSerializer The {@link Serializer} for serialization of the token, if present.
     * @return The created {@link DeadLetterEventEntry}
     */
    DeadLetterEventEntry convert(M message, Serializer eventSerializer, Serializer genericSerializer);

    /**
     * Converts a {@link DeadLetterEventEntry} to a {@link EventMessage} implementation.
     *
     * @param entry             The database entry to convert to a {@link EventMessage}
     * @param eventSerializer   The {@link Serializer} for deserialization of payload and metadata.
     * @param genericSerializer The {@link Serializer} for deserialization of the token, if present.
     * @return The created {@link org.axonframework.eventhandling.deadletter.jpa.DeadLetterEventEntry}
     */
    M convert(DeadLetterEventEntry entry, Serializer eventSerializer, Serializer genericSerializer);

    /**
     * Check whether this converter supports the given {@link DeadLetterEventEntry}.
     *
     * @param message The message to check support for.
     * @return Whether the provided message is supported by this converter.
     */
    boolean canConvert(DeadLetterEventEntry message);

    /**
     * Check whether this converter supports the given {@link EventMessage}.
     *
     * @param message The message to check support for.
     * @return Whether the provided message is supported by this converter.
     */
    boolean canConvert(M message);
}
