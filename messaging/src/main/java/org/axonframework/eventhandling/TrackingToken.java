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
     * Indicates whether this token has processed the event at the position represented by the {@code other}
     * token. This method is similar to {@link #covers(TrackingToken)} but focuses solely on whether the specific
     * position was processed, ignoring differences in how the tokens arrived at their current state.
     * <p>
     * This distinction is important for gap-aware tokens where an event might be within the index range but was never
     * actually processed (i.e., it was a gap). In such cases, {@code covers()} might return {@code false} due to
     * gap set differences even though the position itself was processed, while this method correctly identifies
     * whether the specific position was processed.
     * <p>
     * For most token implementations, this method behaves identically to {@link #covers(TrackingToken)}.
     * Gap-aware implementations should override this to check only position coverage without requiring
     * gap set compatibility.
     *
     * @param other The token representing the position to check
     * @return {@code true} if this token has processed the event at the position represented by {@code other},
     *         otherwise {@code false}
     * @since 4.11.0
     */
    default boolean processed(TrackingToken other) {
        return covers(other);
    }

}
