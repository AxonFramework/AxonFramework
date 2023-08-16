/*
 * Copyright (c) 2010-2023. Axon Framework
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.beans.ConstructorProperties;
import java.io.Serializable;
import java.util.Objects;
import java.util.OptionalLong;

import static org.axonframework.common.Assert.isTrue;

/**
 * Tracking token based on the global sequence number of an event.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class GlobalSequenceTrackingToken implements TrackingToken, Comparable<GlobalSequenceTrackingToken>,
                                                    Serializable {

    private static final long serialVersionUID = -3658606104934080049L;

    private final long globalIndex;

    /**
     * Initializes a {@link GlobalSequenceTrackingToken} from the given {@code globalIndex} of the event.
     *
     * @param globalIndex the global sequence number of the event
     */
    @JsonCreator
    @ConstructorProperties({"globalIndex"})
    public GlobalSequenceTrackingToken(@JsonProperty("globalIndex") long globalIndex) {
        this.globalIndex = globalIndex;
    }

    /**
     * Get the global sequence number of the event
     *
     * @return the global sequence number of the event
     */
    public long getGlobalIndex() {
        return globalIndex;
    }

    /**
     * Returns a new {@link GlobalSequenceTrackingToken} instance that is the sum of this token's sequence number and
     * the given {@code offset}.
     *
     * @param offset the offset between this token's sequence number of that of the returned instance
     * @return a new tracking token with global sequence increased with the given offset
     */
    public GlobalSequenceTrackingToken offsetBy(int offset) {
        return new GlobalSequenceTrackingToken(globalIndex + offset);
    }

    /**
     * Returns a new {@link GlobalSequenceTrackingToken} instance with sequence number incremented by 1.
     *
     * @return a new tracking token with sequence number incremented by 1
     */
    public GlobalSequenceTrackingToken next() {
        return offsetBy(1);
    }

    @Override
    public TrackingToken lowerBound(TrackingToken other) {
        isTrue(other instanceof GlobalSequenceTrackingToken,
               () -> "Incompatible token type provided:" + other.getClass().getSimpleName());

        GlobalSequenceTrackingToken otherToken = (GlobalSequenceTrackingToken) other;
        if (otherToken.globalIndex < this.globalIndex) {
            return otherToken;
        } else {
            return this;
        }
    }

    @Override
    public TrackingToken upperBound(TrackingToken other) {
        isTrue(other instanceof GlobalSequenceTrackingToken,
               () -> "Incompatible token type provided:" + other.getClass().getSimpleName());

        if (((GlobalSequenceTrackingToken) other).globalIndex > this.globalIndex) {
            return other;
        }
        return this;
    }

    @Override
    public boolean covers(TrackingToken other) {
        isTrue(other == null || other instanceof GlobalSequenceTrackingToken,
               () -> "Incompatible token type provided:" + (other != null ? other.getClass().getSimpleName() : "null"));
        GlobalSequenceTrackingToken otherToken = (GlobalSequenceTrackingToken) other;

        return otherToken == null || otherToken.globalIndex <= this.globalIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GlobalSequenceTrackingToken that = (GlobalSequenceTrackingToken) o;
        return globalIndex == that.globalIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(globalIndex);
    }

    @Override
    public String toString() {
        return "IndexTrackingToken{" + "globalIndex=" + globalIndex + '}';
    }

    @Override
    public int compareTo(GlobalSequenceTrackingToken o) {
        return Long.compare(globalIndex, o.globalIndex);
    }

    @Override
    public OptionalLong position() {
        return OptionalLong.of(globalIndex);
    }
}
