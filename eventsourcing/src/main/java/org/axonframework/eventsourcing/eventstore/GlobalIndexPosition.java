/*
 * Copyright (c) 2010-2026. Axon Framework
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
 * An immutable implementation of {@link Position} which represents positions
 * with an index in the global event stream.
 *
 * @author John Hendrikx
 * @since 5.0.0
 */
public final class GlobalIndexPosition implements Position {

    private static final long MINIMUM_INDEX = Long.MIN_VALUE;

    /**
     * Converts the given position to a global index, if possible.
     *
     * @param position A position, cannot be {@code null}.
     * @return A global index.
     * @throws NullPointerException When any argument is {@code null}.
     * @throws IllegalArgumentException When the given position could not be converted.
     */
    public static long toIndex(@Nonnull Position position) {
        return switch (position) {
            case GlobalIndexPosition gip -> gip.index;
            case Position p when p == Position.START -> MINIMUM_INDEX;
            default -> throw new IllegalArgumentException("position must be of type GlobalIndexPosition: " + position);
        };
    }

    private final long index;

    // TODO #4198 make this package private again
    public GlobalIndexPosition(long index) {
        this.index = index;
    }

    @Nonnull
    @Override
    public Position min(@Nonnull Position other) {
        return switch (other) {
            case Position p when p == Position.START -> Position.START;
            case GlobalIndexPosition gip -> index < gip.index ? this : gip;
            default -> throw new IllegalArgumentException("other must be of type GlobalIndexPosition: " + other);
        };
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GlobalIndexPosition that = (GlobalIndexPosition) o;
        return index == that.index;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(index);
    }

    @Override
    public String toString() {
        return "GlobalIndexPosition{index=%d}".formatted(index);
    }
}
