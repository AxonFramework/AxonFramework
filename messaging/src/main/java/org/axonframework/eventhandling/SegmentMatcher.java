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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.eventhandling.async.SequencingPolicy;

import java.util.Objects;

/**
 * Utility class that matches {@link EventMessage EventMessages} against a {@link Segment} based on a
 * {@link SequencingPolicy}.
 * <p>
 * This class uses the sequencing policy to determine the sequence identifier for a message, and then checks if that
 * identifier matches the given segment.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
class SegmentMatcher {

    private final SequencingPolicy sequencingPolicy;

    /**
     * Initialize a SegmentMatcher with the given {@code sequencingPolicy}. This policy is used to extract the sequence
     * identifier from messages, which is then used to match against segments.
     *
     * @param sequencingPolicy A policy that provides the sequence identifier for a given event message.
     */
    public SegmentMatcher(@Nonnull SequencingPolicy sequencingPolicy) {
        Objects.requireNonNull(sequencingPolicy, "SequencingPolicy may not be null");
        this.sequencingPolicy = sequencingPolicy;
    }

    /**
     * Checks whether the given {@code segment} matches the given {@code event}, based on the configured sequencing
     * policy.
     *
     * @param segment The segment to match against.
     * @param event The event to check.
     * @return {@code true} if the event matches the segment, {@code false} otherwise.
     */
    public boolean matches(@Nonnull Segment segment, @Nonnull EventMessage<?> event) {
        Objects.requireNonNull(segment, "Segment may not be null");
        Objects.requireNonNull(event, "EventMessage may not be null");
        return segment.matches(Objects.hashCode(sequenceIdentifier(event)));
    }

    /**
     * Returns the sequence identifier for the given {@code event}, as defined by the configured sequencing policy. If
     * the policy returns {@code null}, the event's identifier is used as a fallback.
     *
     * @param event The event to get the sequence identifier for.
     * @return The sequence identifier for the event, never {@code null}.
     */
    public Object sequenceIdentifier(@Nonnull EventMessage<?> event) {
        Objects.requireNonNull(event, "EventMessage may not be null");
        return sequencingPolicy.getSequenceIdentifierFor(event).orElseGet(event::identifier);
    }
}