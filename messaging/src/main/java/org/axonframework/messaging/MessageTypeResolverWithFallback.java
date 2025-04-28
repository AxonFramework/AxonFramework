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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;

import java.util.Objects;

public class MessageTypeResolverWithFallback implements MessageTypeResolver {

    private final MessageTypeResolver delegate;
    private final MessageTypeResolver fallback;

    public MessageTypeResolverWithFallback(@Nonnull MessageTypeResolver delegate, @Nonnull MessageTypeResolver fallback) {
        Objects.requireNonNull(delegate, "Delegate may not be null");
        Objects.requireNonNull(fallback, "Fallback may not be null");
        this.delegate = delegate;
        this.fallback = fallback;
    }

    @Override
    public MessageType resolve(Class<?> payloadType) {
        try {
            return delegate.resolve(payloadType);
        } catch (MessageTypeNotResolvedException e) {
            return fallback.resolve(payloadType);
        }
    }
}
