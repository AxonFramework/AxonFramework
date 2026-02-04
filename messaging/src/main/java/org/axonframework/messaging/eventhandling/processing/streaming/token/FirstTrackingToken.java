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

package org.axonframework.messaging.eventhandling.processing.streaming.token;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * A special {@link TrackingToken} that indicates the very beginning of an event stream.
 * <p>
 * Used to explicitly represent the first position in a stream instead of using {@code null}.
 *
 * @author TeraSeo
 * @since 5.0.0
 */
final class FirstTrackingToken implements TrackingToken {

    public static final FirstTrackingToken INSTANCE = new FirstTrackingToken();
    private FirstTrackingToken() {

    }

    /**
     * This token is always considered the lower bound since it represents the very beginning.
     */
    @Override
    public TrackingToken lowerBound(TrackingToken other) {
        return this;
    }

    /**
     * The furthest known position is assumed to be the other token, since this token is the earliest possible one.
     */
    @Override
    public TrackingToken upperBound(TrackingToken other) {
        return other;
    }

    /**
     * This token does not cover any other tokens (except itself).
     */
    @Override
    public boolean covers(TrackingToken other) {
        return this.equals(other);
    }

    /**
     * The estimated position of this token is always 0.
     */
    @Override
    public OptionalLong position() {
        return OptionalLong.of(0L);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FirstTrackingToken;
    }

    @Override
    public int hashCode() {
        return Objects.hash("FirstTrackingToken");
    }

    @Override
    public String toString() {
        return "FirstTrackingToken";
    }
}
