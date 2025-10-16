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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;

/**
 * An implementation of {@link Position} based on aggregate sequence numbers.
 *
 * @author John Hendrikx
 * @since 5.0.0
 */
public final class AggregateSequenceNumberPosition implements Position {

    private static final long MINIMUM_SEQUENCE_NUMBER = 0;

    /**
     * Converts the given position to a sequence number, if possible.
     *
     * @param position A position, cannot be {@code null}.
     * @return A sequence number.
     * @throws NullPointerException When any argument is {@code null}.
     * @throws IllegalArgumentException When the given position could not be converted.
     */
    public static long toSequenceNumber(@Nonnull Position position) {
        return switch (position) {
            case AggregateSequenceNumberPosition gip -> gip.sequenceNumber;
            case Position p when p == Position.START -> MINIMUM_SEQUENCE_NUMBER;
            default -> throw new IllegalArgumentException("position must be of type AggregateSequenceNumberPosition: " + position);
        };
    }

    private final long sequenceNumber;

    AggregateSequenceNumberPosition(long sequenceNumber) {
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber cannot be negative");
        }

        this.sequenceNumber = sequenceNumber;
    }

    @Nonnull
    @Override
    public Position min(@Nonnull Position other) {
        return switch (other) {
            case Position p when p == Position.START -> Position.START;
            case AggregateSequenceNumberPosition asnp -> sequenceNumber < asnp.sequenceNumber ? this : asnp;
            default -> throw new IllegalArgumentException("other must be of type AggregateSequenceNumberPosition: " + other);
        };
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AggregateSequenceNumberPosition that = (AggregateSequenceNumberPosition) o;
        return sequenceNumber == that.sequenceNumber;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(sequenceNumber);
    }

    @Override
    public String toString() {
        return "AggregateSequenceNumberPosition{sequenceNumber=%d}".formatted(sequenceNumber);
    }
}
