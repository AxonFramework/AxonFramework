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

package org.axonframework.eventsourcing.eventstore.legacy;

import org.axonframework.eventsourcing.eventstore.TrackingToken;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

/**
 * @author Rene de Waele
 */
public class LegacyTrackingToken implements TrackingToken {

    private final Instant timestamp;
    private final String aggregateIdentifier;
    private final long sequenceNumber;

    public LegacyTrackingToken(Instant timestamp, String aggregateIdentifier, long sequenceNumber) {
        this.timestamp = timestamp;
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public boolean isGuaranteedNext(TrackingToken otherToken) {
        return false;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LegacyTrackingToken that = (LegacyTrackingToken) o;
        return sequenceNumber == that.sequenceNumber &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(aggregateIdentifier, that.aggregateIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, aggregateIdentifier, sequenceNumber);
    }

    @Override
    public int compareTo(TrackingToken o) {
        LegacyTrackingToken other = (LegacyTrackingToken) o;
        return Comparator.comparing(LegacyTrackingToken::getTimestamp)
                .thenComparingLong(LegacyTrackingToken::getSequenceNumber)
                .thenComparing(LegacyTrackingToken::getAggregateIdentifier).compare(this, other);
    }

    @Override
    public String toString() {
        return "LegacyTrackingToken{" +
                "timestamp=" + timestamp +
                ", aggregateIdentifier='" + aggregateIdentifier + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}
