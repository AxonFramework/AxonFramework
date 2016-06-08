/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.annotation;

import org.axonframework.messaging.Message;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;

public abstract class WrappedMessageHandler<T> implements MessageHandler<T> {

    private final MessageHandler<T> delegate;

    protected WrappedMessageHandler(MessageHandler<T> delegate) {
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
    public boolean canHandle(Message<?> message) {
        return delegate.canHandle(message);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <HT> Optional<HT> unwrap(Class<HT> handlerType) {
        if (handlerType.isInstance(this)) {
            return (Optional<HT>) Optional.of(this);
        }
        return delegate.unwrap(handlerType);
    }

    @Override
    public Optional<Map<String, Object>> annotationAttributes(Class<? extends Annotation> annotationType) {
        return delegate.annotationAttributes(annotationType);
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return delegate.hasAnnotation(annotationType);
    }
}
