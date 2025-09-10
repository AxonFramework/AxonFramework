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

package org.axonframework.eventhandling.sequencing;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of {@link SequencingPolicy} that combines two policies in a fallback pattern. When the primary
 * policy fails to determine a sequence identifier for an event (returns {@code Optional.empty()}), this implementation
 * will delegate to a secondary fallback policy.
 * <p>
 * This allows for composing sequencing strategies where certain event types might be handled by specialized
 * policies, falling back to more generic approaches when specialized sequencing fails.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class HierarchicalSequencingPolicy implements SequencingPolicy {

    private final SequencingPolicy primary;
    private final SequencingPolicy secondary;

    /**
     * Initializes a new instance with the given primary {@code delegate} and {@code fallback} policies.
     *
     * @param primary The primary policy to attempt sequence identification with first, not {@code null}.
     * @param secondary The fallback policy to use when the delegate fails, not {@code null}.
     * @throws NullPointerException When either the {@code delegate} or {@code fallback} is {@code null}.
     */
    public HierarchicalSequencingPolicy(@Nonnull SequencingPolicy primary, @Nonnull SequencingPolicy secondary) {
        Objects.requireNonNull(primary, "Primary may not be null.");
        Objects.requireNonNull(secondary, "Secondary may not be null.");
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public Optional<Object> getSequenceIdentifierFor(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
        return primary.getSequenceIdentifierFor(event, context)
                      .or(() -> secondary.getSequenceIdentifierFor(event, context));
    }
}