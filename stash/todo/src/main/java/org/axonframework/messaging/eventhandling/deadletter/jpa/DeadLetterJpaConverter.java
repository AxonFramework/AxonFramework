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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;

/**
 * Converter that can convert an {@link EventMessage} to a {@link DeadLetterEventEntry} and vice versa.
 * <p>
 * Context resources can be stored alongside the message by this converter. Implementations decide which resources to
 * serialize and how to restore them, typically based on configuration provided by the queue.
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
     * The context may carry resources that the converter serializes alongside the message, depending on configuration.
     *
     * @param message          The message to convert.
     * @param context          The context containing resources to be serialized.
     * @param eventConverter   The {@link EventConverter} for conversion of payload and metadata.
     * @param genericConverter The {@link Converter} used for conversion of any configured context resources.
     * @return The created {@link DeadLetterEventEntry}.
     */
    @Nonnull
    DeadLetterEventEntry convert(@Nonnull M message, @Nullable Context context, @Nonnull EventConverter eventConverter, @Nonnull Converter genericConverter);

    /**
     * Converts a {@link DeadLetterEventEntry} to a {@link MessageStream.Entry} containing the {@link EventMessage}
     * implementation and a {@link Context} with restored resources.
     * <p>
     * The returned entry's context contains restored resources that were stored when the dead letter was enqueued.
     *
     * @param entry            The database entry to convert.
     * @param eventConverter   The {@link EventConverter} for deserialization of payload and metadata.
     * @param genericConverter The {@link Converter} used for deserialization of configured context resources.
     * @return A {@link MessageStream.Entry} containing the message and context with restored resources.
     */
    @Nonnull
    MessageStream.Entry<M> convert(@Nonnull DeadLetterEventEntry entry, @Nonnull EventConverter eventConverter, @Nonnull Converter genericConverter);
}
