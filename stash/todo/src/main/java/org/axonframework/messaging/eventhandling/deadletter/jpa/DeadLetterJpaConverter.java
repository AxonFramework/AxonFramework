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

package org.axonframework.messaging.eventhandling.deadletter.jpa;

import org.axonframework.conversion.Converter;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;

/**
 * Converter that can convert an {@link EventMessage} to a {@link DeadLetterEventEntry} and vice versa.
 * <p>
 * In AF5, tracking tokens and domain info (aggregate identifier, type, sequence number) are no longer part of message
 * subtypes. Instead, they are stored as context resources. This converter handles storing these resources as separate
 * columns in the database and restoring them when converting back.
 *
 * @param <M> The type of the event message this converter will convert.
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public interface DeadLetterJpaConverter<M extends EventMessage> {

    /**
     * Converts an {@link EventMessage} implementation and its associated {@link Context} to a
     * {@link DeadLetterEventEntry}.
     * <p>
     * The context is used to extract tracking token and domain info (aggregate identifier, type, sequence number) if
     * present, as these are stored as context resources in AF5 rather than message subtypes.
     *
     * @param message          The message to convert.
     * @param context          The context containing resources such as tracking token and domain info.
     * @param eventConverter   The {@link EventConverter} for conversion of payload and metadata.
     * @param genericConverter The {@link Converter} for conversion of the tracking token, if present.
     * @return The created {@link DeadLetterEventEntry}.
     */
    DeadLetterEventEntry convert(M message, Context context, EventConverter eventConverter, Converter genericConverter);

    /**
     * Converts a {@link DeadLetterEventEntry} to a {@link MessageStream.Entry} containing the {@link EventMessage}
     * implementation and a {@link Context} with restored resources.
     * <p>
     * The returned entry's context contains the restored tracking token and domain info (aggregate identifier, type,
     * sequence number) if they were stored when the dead letter was enqueued.
     *
     * @param entry            The database entry to convert.
     * @param eventConverter   The {@link EventConverter} for deserialization of payload and metadata.
     * @param genericConverter The {@link Converter} for deserialization of the tracking token, if present.
     * @return A {@link MessageStream.Entry} containing the message and context with restored resources.
     */
    MessageStream.Entry<M> convert(DeadLetterEventEntry entry, EventConverter eventConverter, Converter genericConverter);

    /**
     * Check whether this converter supports converting the given {@link DeadLetterEventEntry} back to a message.
     *
     * @param entry The entry to check support for.
     * @return Whether the provided entry is supported by this converter.
     */
    boolean canConvert(DeadLetterEventEntry entry);

    /**
     * Check whether this converter supports converting the given {@link EventMessage} to an entry.
     *
     * @param message The message to check support for.
     * @return Whether the provided message is supported by this converter.
     */
    boolean canConvert(M message);
}
