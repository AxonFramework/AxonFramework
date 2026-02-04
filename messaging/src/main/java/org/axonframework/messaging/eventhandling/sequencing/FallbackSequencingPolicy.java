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

package org.axonframework.messaging.eventhandling.sequencing;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of {@link SequencingPolicy} that provides exception-based fallback behavior. When the delegate
 * policy throws a specified exception type, this implementation will catch it and delegate to a fallback policy.
 * <p>
 * This allows for composing sequencing strategies where certain policies might fail with exceptions for unsupported
 * event types, falling back to more generic approaches when exceptions occur.
 *
 * @param <E> The type of exception to catch and handle.
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class FallbackSequencingPolicy<E extends Exception> implements SequencingPolicy {

    private final SequencingPolicy delegate;
    private final SequencingPolicy fallback;
    private final Class<E> exceptionType;

    /**
     * Initializes a new instance with the given {@code delegate} policy, {@code fallback} policy, and
     * {@code exceptionType} to catch.
     *
     * @param delegate      The primary policy to attempt sequence identification with first, not {@code null}.
     * @param fallback      The fallback policy to use when the delegate throws the specified exception, not
     *                      {@code null}.
     * @param exceptionType The type of exception to catch from the delegate policy, not {@code null}.
     * @throws NullPointerException When any of the parameters is {@code null}.
     */
    public FallbackSequencingPolicy(@Nonnull SequencingPolicy delegate,
                                    @Nonnull SequencingPolicy fallback,
                                    @Nonnull Class<E> exceptionType) {
        this.delegate =  Objects.requireNonNull(delegate, "Delegate may not be null.");
        this.fallback = Objects.requireNonNull(fallback, "Fallback may not be null.");
        this.exceptionType = Objects.requireNonNull(exceptionType, "Exception type may not be null.");;
    }

    @Override
    public Optional<Object> getSequenceIdentifierFor(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
        try {
            return delegate.getSequenceIdentifierFor(event, context);
        } catch (Exception e) {
            if (exceptionType.isInstance(e)) {
                return fallback.getSequenceIdentifierFor(event, context);
            }
            throw e;
        }
    }
}