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

import org.axonframework.eventhandling.async.SequencingPolicy;

import java.util.Objects;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Utility class that matches {@link EventMessage}s against a {@link Segment} based on a {@link SequencingPolicy}.
 * <p>
 * This class uses the sequencing policy to determine the sequence identifier for a message, and then checks if that
 * identifier matches the given segment.
 *
 * @author Mateusz Nowak
 * @since 5.0
 */
public class SegmentMatcher {

    private final SequencingPolicy<? super EventMessage<?>> sequencingPolicy;

    /**
     * Initialize a SegmentMatcher with the given {@code sequencingPolicy}. The sequencing policy is used to extract
     * the sequence identifier from messages, which is then used to match against segments.
     *
     * @param sequencingPolicy the policy defining the sequence identifiers to use for segment matching
     */
    public SegmentMatcher(SequencingPolicy<? super EventMessage<?>> sequencingPolicy) {
        this.sequencingPolicy = sequencingPolicy;
    }

    /**
     * Checks whether the given {@code segment} matches the given {@code message}, based on the configured
     * sequencing policy.
     *
     * @param segment the segment to match against
     * @param message the message to check
     * @return {@code true} if the message matches the segment, {@code false} otherwise
     */
    public boolean matches(Segment segment, EventMessage<?> message) {
        return segment.matches(Objects.hashCode(sequenceIdentifier(message)));
    }

    /**
     * Returns the sequence identifier for the given {@code event}, as defined by the configured sequencing policy.
     * If the policy returns {@code null}, the event's identifier is used as a fallback.
     *
     * @param event the event to get the sequence identifier for
     * @return the sequence identifier for the event, never {@code null}
     */
    public Object sequenceIdentifier(EventMessage<?> event) {
        return getOrDefault(sequencingPolicy.getSequenceIdentifierFor(event), event::getIdentifier);
    }

    /**
     * Returns the {@link SequencingPolicy} used by this matcher.
     *
     * @return the sequencing policy used to determine sequence identifiers
     */
    public SequencingPolicy<? super EventMessage<?>> getSequencingPolicy() {
        return sequencingPolicy;
    }
}
