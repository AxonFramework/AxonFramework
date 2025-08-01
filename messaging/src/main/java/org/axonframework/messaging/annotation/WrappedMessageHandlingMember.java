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

package org.axonframework.messaging.annotation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Optional;

/**
 * Abstract implementation of a {@link MessageHandlingMember} that delegates to a wrapped MessageHandlingMember. Extend
 * this class to provide additional functionality to the delegate member.
 *
 * @param <T> the entity type
 * @author Allard Buijze
 * @since 3.0
 */
public abstract class WrappedMessageHandlingMember<T> implements MessageHandlingMember<T> {

    private final MessageHandlingMember<T> delegate;

    /**
     * Initializes the member using the given {@code delegate}.
     *
     * @param delegate the actual message handling member to delegate to
     */
    protected WrappedMessageHandlingMember(MessageHandlingMember<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Class<?> payloadType() {
        return delegate.payloadType();
    }

    @Override
    public int priority() {
        return delegate.priority();
    }

    @Override
    public boolean canHandle(@Nonnull Message<?> message, @Nonnull ProcessingContext context) {
        return delegate.canHandle(message, context);
    }

    @Override
    public Object handleSync(@Nonnull Message<?> message, @Nonnull ProcessingContext context,  T target) throws Exception {
        return delegate.handleSync(message, context, target);
    }

    @Override
    public MessageStream<?> handle(@Nonnull Message<?> message,
                                   @Nonnull ProcessingContext context,
                                   @Nullable T target) {
        return delegate.handle(message, context, target);
    }

    @Override
    public boolean canHandleType(@Nonnull Class<?> payloadType) {
        return delegate.canHandleType(payloadType);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean canHandleMessageType(@Nonnull Class<? extends Message> messageType) {
        return delegate.canHandleMessageType(messageType);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <HT> Optional<HT> unwrap(Class<HT> handlerType) {
        if (handlerType.isInstance(this)) {
            return (Optional<HT>) Optional.of(this);
        } else if (handlerType.isInstance(delegate)) {
            return (Optional<HT>) Optional.of(delegate);
        }
        return delegate.unwrap(handlerType);
    }

    @Override
    public <R> Optional<R> attribute(String attributeKey) {
        return delegate.attribute(attributeKey);
    }
}
