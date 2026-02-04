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

package org.axonframework.messaging.core.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of a {@link ParameterResolver} that resolves the Message payload as parameter in a handler method.
 *
 * @author Allard Buijze
 * @since 3.0.0
 */
public class PayloadParameterResolver implements ParameterResolver<Object> {

    private final Class<?> payloadType;

    /**
     * Initializes a new {@code PayloadParameterResolver} for a method parameter of given {@code payloadType}. This
     * parameter resolver matches with a message if the payload of the message is assignable to the given
     * {@code payloadType}.
     *
     * @param payloadType the parameter type
     */
    public PayloadParameterResolver(Class<?> payloadType) {
        this.payloadType = payloadType;
    }

    @Nonnull
    @Override
    public CompletableFuture<Object> resolveParameterValue(@Nonnull ProcessingContext context) {
        return CompletableFuture.completedFuture(Message.fromContext(context).payload());
    }

    @Override
    public boolean matches(@Nonnull ProcessingContext context) {
        return Optional.ofNullable(Message.fromContext(context))
                       .map(Message::payloadType)
                       .map(payloadType::isAssignableFrom)
                       .orElse(false);
    }

    @Override
    public Class<?> supportedPayloadType() {
        return payloadType;
    }
}
