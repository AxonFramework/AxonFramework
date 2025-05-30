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
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Optional;

/**
 * Implementation of a {@link ParameterResolver} that resolves the Message payload as parameter in a handler method.
 */
public class PayloadParameterResolver implements ParameterResolver<Object> {

    private final Class<?> payloadType;

    /**
     * Initializes a new {@link PayloadParameterResolver} for a method parameter of given {@code payloadType}. This
     * parameter resolver matches with a message if the payload of the message is assignable to the given {@code
     * payloadType}.
     *
     * @param payloadType the parameter type
     */
    public PayloadParameterResolver(Class<?> payloadType) {
        this.payloadType = payloadType;
    }

    @Override
    public Object resolveParameterValue(@Nonnull ProcessingContext context) {
        return Message.fromContext(context).getPayload();
    }

    @Override
    public boolean matches(@Nonnull ProcessingContext context) {
        return Optional.ofNullable(Message.fromContext(context))
                       .map(Message::getPayloadType)
                       .map(payloadType::isAssignableFrom)
                       .orElse(false);
    }

    @Override
    public Class<?> supportedPayloadType() {
        return payloadType;
    }
}
