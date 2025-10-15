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

package org.axonframework.messaging.annotations;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.unitofwork.ProcessingContext;

/**
 * Interface for a mechanism that resolves handler method parameter values from a given {@link ProcessingContext}.
 *
 * @param <T> The type of parameter returned by this resolver.
 * @author Allard Buijze
 * @since 2.0.0
 */
public interface ParameterResolver<T> {

    /**
     * Resolves the parameter value to for the {@code context}, or {@code null} if no suitable parameter value
     * can be resolved.
     *
     * @param context The current processing context.
     * @return The parameter value for the handler.
     */
    @Nullable
    T resolveParameterValue(@Nonnull ProcessingContext context);

    /**
     * Indicates whether this resolver is capable of providing a value for the given {@code context}.
     *
     * @param context The current processing context.
     * @return Returns {@code true} if this resolver can provide a value for the message, otherwise {@code false}.
     */
    boolean matches(@Nonnull ProcessingContext context);

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
