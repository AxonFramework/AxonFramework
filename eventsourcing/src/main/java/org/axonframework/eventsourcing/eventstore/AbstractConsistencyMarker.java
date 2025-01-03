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
 * Abstract implementation of the {@link ConsistencyMarker} interface that implements the required comparisons with the
 * {@link org.axonframework.eventsourcing.eventstore.ConsistencyMarker#ORIGIN} and
 * {@link org.axonframework.eventsourcing.eventstore.ConsistencyMarker#INFINITY} consistency markers. The
 * implementation-specific comparisons are left to the subclasses.
 *
 * @param <T> The type of ConsistencyMarker this implementation expects. It's typically the same as the implementing
 *            class.
 */
public abstract class AbstractConsistencyMarker<T extends ConsistencyMarker> implements ConsistencyMarker {

    /**
     * @throws ClassCastException when the given marker is not of the expected type
     */
    @Override
    public ConsistencyMarker lowerBound(ConsistencyMarker other) {
        if (other == ConsistencyMarker.INFINITY || other == ConsistencyMarker.ORIGIN) {
            return other.lowerBound(this);
        }
        //noinspection unchecked
        return doLowerBound((T) other);
    }

    /**
     * @throws ClassCastException when the given marker is not of the expected type
     */
    @Override
    public ConsistencyMarker upperBound(ConsistencyMarker other) {
        if (other == ConsistencyMarker.INFINITY || other == ConsistencyMarker.ORIGIN) {
            return other.upperBound(this);
        }
        //noinspection unchecked
        return doUpperBound((T) other);
    }

    /**
     * Calculate the lower bound of {@code this} marker and given {@code other} marker. The result must represent the
     * lowest of the given markers. This may either be on of {@code this} or the {@code other} marker, or any other that
     * represents their lower bound.
     *
     * @param other The other marker
     * @return a marker representing the lower bound of this and the other marker
     */
    protected abstract ConsistencyMarker doLowerBound(T other);

    /**
     * Calculate the upper bound of {@code this} marker and given {@code other} marker. The result must represent the
     * highest of the given markers. This may either be on of {@code this} or the {@code other} marker, or any other
     * that represents their upper bound.
     *
     * @param other The other marker
     * @return a marker representing the upper bound of this and the other marker
     */
    protected abstract ConsistencyMarker doUpperBound(T other);
}
