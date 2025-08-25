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
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.DelegatingMessageConverter;
import org.axonframework.messaging.MessageConverter;
import org.axonframework.serialization.Converter;

import java.lang.reflect.Type;
import java.util.Objects;

/**
 * An {@link EventConverter} implementation delegating conversion operations to a {@link Converter}.
 * <p>
 * Useful to ensure callers of this component <b>only</b> convert {@link EventMessage} implementations.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class DelegatingEventConverter implements EventConverter {

    private final MessageConverter messageConverter;

    /**
     * Constructs a {@code DelegatingEventConverter}, delegating operations to a {@link DelegatingMessageConverter}
     * build with the given {@code converter}.
     *
     * @param converter The converter to construct a {@link DelegatingMessageConverter} with to delegate all conversion
     *                  operations to.
     */
    public DelegatingEventConverter(@Nonnull Converter converter) {
        this(new DelegatingMessageConverter(Objects.requireNonNull(converter, "The Converter must not be null.")));
    }

    /**
     * Constructs a {@code DelegatingEventConverter}, delegating operations to the given {@code converter}.
     *
     * @param messageConverter The converter to delegate all conversion operations to.
     */
    public DelegatingEventConverter(@Nonnull MessageConverter messageConverter) {
        this.messageConverter = Objects.requireNonNull(messageConverter, "The Converter must not be null.");
    }

    @Nullable
    @Override
    public <E extends EventMessage, T> T convertPayload(@Nonnull E event, @Nonnull Type targetType) {
        return messageConverter.convertPayload(event, targetType);
    }

    @Override
    public <E extends EventMessage> E convertEvent(@Nonnull E event, @Nonnull Type targetType) {
        return messageConverter.convertMessage(event, targetType);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("messageConverter", messageConverter);
    }

    /**
     * Returns the {@link MessageConverter} this {@code EventConverter} delegates too.
     * <p>
     * Used to automatically construct other instances with the exact same {@code MessageConverter}.
     *
     * @return The {@link MessageConverter} this {@code EventConverter} delegates too.
     */
    @Internal
    public MessageConverter converter() {
        return messageConverter;
    }
}
