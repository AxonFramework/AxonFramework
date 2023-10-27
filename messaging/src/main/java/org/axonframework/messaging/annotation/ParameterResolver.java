/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.messaging.Message;

/**
 * Interface for a mechanism that resolves handler method parameter values from a given {@link Message}.
 *
 * @param <T> The type of parameter returned by this resolver
 * @author Allard Buijze
 * @since 2.0
 */
public interface ParameterResolver<T> {

    /**
     * Resolves the parameter value to use for the given {@code message}, or {@code null} if no suitable
     * parameter value can be resolved.
     *
     * @param message The message to resolve the value from
     * @return the parameter value for the handler
     */
    T resolveParameterValue(Message<?> message);

    /**
     * Indicates whether this resolver is capable of providing a value for the given {@code message}.
     *
     * @param message The message to evaluate
     * @return {@code true} if this resolver can provide a value for the message, otherwise {@code false}
     */
    boolean matches(Message<?> message);

    /**
     * Returns the class of the payload that is supported by this resolver. Defaults to the {@link Object} class
     * indicating that the payload type is irrelevant for this resolver.
     *
     * @return The class of the payload that is supported by this resolver
     */
    default Class<?> supportedPayloadType() {
        return Object.class;
    }
}
