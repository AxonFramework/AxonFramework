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
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.core.Message;

import java.lang.reflect.Type;
import java.util.Objects;

/**
 * A {@link MessageConverter} implementation delegating conversion operations to a {@link Converter}.
 * <p>
 * Useful to ensure callers of this component <b>only</b> convert {@link Message} implementations.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class DelegatingMessageConverter implements MessageConverter {

    private final Converter delegate;

    /**
     * Constructs a {@code DelegatingMessageConverter}, delegating operations to the given {@code converter}.
     *
     * @param delegate The converter to delegate all conversion operations to.
     */
    public DelegatingMessageConverter(@Nonnull Converter delegate) {
        this.delegate = Objects.requireNonNull(delegate, "The Converter must not be null.");
    }

    @Nullable
    @Override
    public <T> T convert(@Nullable Object input, @Nonnull Type targetType) {
        return delegate.convert(input, targetType);
    }

    @Override
    @Nullable
    public <M extends Message, T> T convertPayload(@Nonnull M message, @Nonnull Type targetType) {
        return message.payloadAs(targetType, delegate);
    }

    @Override
    @Nonnull
    public <M extends Message> M convertMessage(@Nonnull M message, @Nonnull Type targetType) {
        //noinspection unchecked
        return (M) message.withConvertedPayload(targetType, delegate);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }

    /**
     * Returns the delegate {@link Converter} this {@code MessageConverter} delegates to.
     * <p>
     * Useful to construct other instances with the exact same {@code Converter}.
     *
     * @return The {@link Converter} this {@code MessageConverter} delegates to.
     */
    @Internal
    public Converter delegate() {
        return delegate;
    }
}
