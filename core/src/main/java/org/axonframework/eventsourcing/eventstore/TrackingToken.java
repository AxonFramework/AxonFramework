/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

/**
 * Describes a token that is used to identify the position of an event in an event stream. Event processors use this
 * token to keep track of the events it has processed and still need to process.
 * <p>
 * A tracking token is {@link Comparable} to another token of the same type.
 *
 * @author Rene de Waele
 */
public interface TrackingToken extends Comparable<TrackingToken> {

    /**
     * Returns {@code true} if the {@code otherToken} is guaranteed to be the next token in a stream of events after
     * this token.
     * <p>
     * Note that a return value of {@code false} does not exclude the possibility that the otherToken is the next event
     * in the stream.
     *
     * @param otherToken The token to inspect
     * @return {@code true} if the given otherToken is guaranteed to be the next token after this token
     */
    boolean isGuaranteedNext(TrackingToken otherToken);

    /**
     * Check if a given {@code otherToken} comes after the current token. Note that a return value of {@code true} does
     * not require that the otherToken is the next token in a stream of events, only that it is 'larger' than the
     * current token.
     *
     * @param otherToken the token to compare this token to
     * @return {@code true} if the given otherToken comes after this token.
     */
    default boolean isAfter(TrackingToken otherToken) {
        return otherToken == null || this.compareTo(otherToken) > 0;
    }

}
