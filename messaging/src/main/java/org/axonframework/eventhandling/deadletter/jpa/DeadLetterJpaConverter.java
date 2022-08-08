/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventhandling.deadletter.jpa;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.serialization.Serializer;

/**
 * Converter that can convert a {@link EventMessage} to a {@link DeadLetterEntryMessage} and vice versa.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public interface DeadLetterJpaConverter<M extends EventMessage<?>> {

    /**
     * Converts an {@link EventMessage} to a {@link DeadLetterEntryMessage}.
     *
     * @param message    The message to convert.
     * @param serializer The {@link Serializer} to use for payload and metadata.
     * @return The created {@link DeadLetterEntryMessage}
     */
    DeadLetterEntryMessage toEntry(M message, Serializer serializer);

    /**
     * Converts a {@link DeadLetterEntryMessage} to a {@link EventMessage}.
     *
     * @param entry      The database entry to convert to a {@link EventMessage}
     * @param serializer The {@link Serializer} to use for payload and metadata.
     * @return The created {@link DeadLetterEntryMessage}
     */
    M fromEntry(DeadLetterEntryMessage entry, Serializer serializer);

    /**
     * Check whether this converter supports the given {@link DeadLetterEntryMessage}.
     *
     * @param message The message to check support for.
     * @return Whether the provided message is supported by this converter.
     */
    boolean canConvert(DeadLetterEntryMessage message);

    /**
     * Check whether this converter supports the given {@link EventMessage}.
     *
     * @param message The message to check support for.
     * @return Whether the provided message is supported by this converter.
     */
    boolean canConvert(EventMessage<?> message);
}
