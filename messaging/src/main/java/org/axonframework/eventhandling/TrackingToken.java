/*
 * Copyright (c) 2010-2022. Axon Framework
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

import java.util.OptionalLong;

/**
 * Tag interface identifying a token that is used to identify the position of an event in an event stream. Event
 * processors use this token to keep track of the events they have processed and still need to process.
 *
 * @author Rene de Waele
 */
public interface TrackingToken {

    /**
     * Returns a token that represents the lower bound between this and the {@code other} token. Effectively, the
     * returned token will cause messages not received by both this and the {@code other} token to be redelivered.
     *
     * @param other The token to compare to this one
     * @return The token representing the lower bound of the two
     */
    TrackingToken lowerBound(TrackingToken other);

    /**
     * Returns the token that represents the furthest possible position in a stream that either this token or the given
     * {@code other} represents. Effectively, this means this token will only deliver messages that neither this, nor
     * the other have been received.
     *
     * @param other The token to compare this token to
     * @return a token that represents the furthest position of this or the other stream
     */
    TrackingToken upperBound(TrackingToken other);

    /**
     * Indicates whether this token covers the {@code other} token completely. That means that this token represents a
     * position in a stream that has received all of the messages that a stream represented by the {@code other} token
     * has received.
     * <p>
     * Note that this operation is only safe when comparing tokens obtained from messages from the same
     * {@link org.axonframework.messaging.StreamableMessageSource}.
     *
     * @param other The token to compare to this one
     * @return {@code true} if this token covers the other, otherwise {@code false}
     */
    boolean covers(TrackingToken other);

    /**
     * Return the estimated relative position this token represents.
     * In case no estimation can be given an {@code OptionalLong.empty()} will be returned.
     *
     * @return the estimated relative position of this token
     */
    default OptionalLong position() {
        return OptionalLong.empty();
    }

    /**
     * Indicates whether this token is at the same position in the stream as the {@code other} token,
     * regardless of gap differences.
     * <p>
     * Unlike {@link #covers(TrackingToken)}, which checks if all events seen by one token are also seen by another,
     * this method only compares the position (index) in the stream. Two tokens overlap if they represent
     * the same position, even if they have different gaps.
     * <p>
     * This is useful for replay detection where we need to know if we've reached a specific position,
     * without caring about gap semantics.
     *
     * @param other The token to compare to this one
     * @return {@code true} if this token is at the same position as the other, otherwise {@code false}
     * @since 4.11.0
     */
    default boolean overlaps(TrackingToken other) {
        // Default implementation uses covers() in both directions as a proxy for same position
        // Implementations should override this with more efficient position-based comparison
        return this.covers(other) && other.covers(this);
    }

    /**
     * Indicates whether this token has seen the position represented by the {@code other} token,
     * without considering gap containment semantics.
     * <p>
     * Unlike {@link #covers(TrackingToken)}, which requires that all gaps in this token are also present
     * in the other token, this method only checks if the specific position (index) of the other token
     * was seen by this token. This means:
     * <ul>
     *   <li>The other token's index must be at or before this token's index</li>
     *   <li>The other token's index must not be a gap in this token</li>
     * </ul>
     * <p>
     * This is useful for replay detection where we need to know if a specific event position was processed
     * before, regardless of what gaps exist in either token.
     *
     * @param other The token to compare to this one
     * @return {@code true} if the position represented by other was seen by this token, otherwise {@code false}
     * @since 4.11.0
     */
    default boolean coversPosition(TrackingToken other) {
        // Default implementation falls back to covers()
        // Implementations with gap semantics should override this
        return this.covers(other);
    }

}
