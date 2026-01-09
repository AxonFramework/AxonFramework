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
     * Indicates whether {@code this} token is at the exact same spot in the event stream as the {@code other} token.
     * <p>
     * This method is particularly useful when comparing tokens from different points in time, such as during
     * replay detection, where token implementations may naturally differ.
     * <p>
     * By default, this method checks bidirectional coverage: {@code this.covers(other) && other.covers(this)},
     * which ensures both tokens are at the same position.
     *
     * @param other The token to validate against {@code this} token.
     * @return {@code true} if this token is at the same location as the other token, otherwise {@code false}.
     *         Returns {@code false} if {@code other} is {@code null}.
     * @see #covers(TrackingToken)
     * @since 4.12.3
     */
    default boolean samePositionAs(TrackingToken other) {
        return other != null && covers(other) && other.covers(this);
    }

}
