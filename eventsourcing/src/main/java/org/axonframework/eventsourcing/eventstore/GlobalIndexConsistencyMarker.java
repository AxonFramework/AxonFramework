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

/**
 * {@link ConsistencyMarker} implementation that uses a single `long` to represent a position in an event stream.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public class GlobalIndexConsistencyMarker extends AbstractConsistencyMarker<GlobalIndexConsistencyMarker> {

    private final long position;

    /**
     * Creates a marker for the given {@code position}.
     *
     * @param position The position in the event stream this marker represents.
     */
    public GlobalIndexConsistencyMarker(long position) {
        this.position = position;
    }

    /**
     * Utility function to resolve the position from given {@code consistencyMarker}. This implementation takes into
     * account that the given {@code consistencyMarker} may be either {@link ConsistencyMarker#ORIGIN} or
     * {@link ConsistencyMarker#INFINITY}.
     *
     * @param consistencyMarker The marker to retrieve the position from.
     * @return a long representation of the position described by the consistency marker.
     */
    public static long position(ConsistencyMarker consistencyMarker) {
        if (consistencyMarker instanceof GlobalIndexConsistencyMarker gicm) {
            return gicm.position;
        } else if (consistencyMarker == ConsistencyMarker.ORIGIN) {
            return 0;
        } else if (consistencyMarker == ConsistencyMarker.INFINITY) {
            return Integer.MAX_VALUE;
        }
        throw new IllegalArgumentException(consistencyMarker + " is not a global index consistency marker");
    }

    @Override
    protected ConsistencyMarker doLowerBound(GlobalIndexConsistencyMarker other) {
        return other.position < this.position ? other : this;
    }

    @Override
    protected ConsistencyMarker doUpperBound(GlobalIndexConsistencyMarker other) {
        return other.position > this.position ? other : this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GlobalIndexConsistencyMarker that = (GlobalIndexConsistencyMarker) o;
        return position == that.position;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(position);
    }

    @Override
    public String toString() {
        return "GlobalIndexConsistencyMarker{" +
                "position=" + position +
                '}';
    }
}
