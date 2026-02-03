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

package org.axonframework.messaging.eventhandling.conversion;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.core.conversion.DelegatingMessageConverter;
import org.axonframework.messaging.core.conversion.MessageConverter;
import org.axonframework.messaging.eventhandling.EventMessage;

import java.lang.reflect.Type;
import java.util.Objects;

/**
 * An {@link EventConverter} implementation delegating conversion operations to a {@link MessageConverter}.
 * <p>
 * Useful to ensure callers of this component <b>only</b> convert {@link EventMessage} implementations.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class DelegatingEventConverter implements EventConverter {

    private final MessageConverter delegate;

    /**
     * Constructs a {@code DelegatingEventConverter}, delegating operations to a {@link DelegatingMessageConverter}
     * build with the given {@code converter}.
     *
     * @param converter The converter to construct a {@link DelegatingMessageConverter} with to delegate all conversion
     *                  operations to.
     */
    public DelegatingEventConverter(@Nonnull Converter converter) {
        this(converter instanceof MessageConverter messageConverter
                     ? messageConverter
                     : new DelegatingMessageConverter(converter));
    }

    /**
     * Constructs a {@code DelegatingEventConverter}, delegating operations to the given {@code converter}.
     *
     * @param delegate The converter to delegate all conversion operations to.
     */
    public DelegatingEventConverter(@Nonnull MessageConverter delegate) {
        this.delegate = Objects.requireNonNull(delegate, "The Converter must not be null.");
    }

    @Nullable
    @Override
    public <T> T convert(@Nullable Object input, @Nonnull Type targetType) {
        return delegate.convert(input, targetType);
    }

    @Override
    @Nullable
    public <E extends EventMessage, T> T convertPayload(@Nonnull E event, @Nonnull Type targetType) {
        return delegate.convertPayload(event, targetType);
    }

    @Override
    @Nonnull
    public <E extends EventMessage> E convertEvent(@Nonnull E event, @Nonnull Type targetType) {
        return delegate.convertMessage(event, targetType);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("messageConverter", delegate);
    }

    /**
     * Returns the {@link MessageConverter} this {@code EventConverter} delegates to.
     * <p>
     * Useful to construct other instances with the exact same {@code Converter}.
     *
     * @return The {@link MessageConverter} this {@code EventConverter} delegates to.
     */
    @Internal
    public MessageConverter delegate() {
        return delegate;
    }
}
