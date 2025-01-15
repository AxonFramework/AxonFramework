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

import org.axonframework.messaging.Context;

/**
 * Interface representing a point in an Event Stream up to where certain models have been made up-to-date. Typically,
 * consistency markers are provided by operations on an event stream that provide events for event sourcing purposes.
 * <p/>
 * Consistency Markers are used to identify the expected position new events should be inserted at, in order to avoid
 * conflicts. If the actual insert position doesn't match the position described in this ConsistencyMarker, there may
 * have been a concurrency conflict.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @author Milan Savic
 * @author Sara Pellegrini
 * @author Michal Negacz
 * @author Marco Amann
 * @since 5.0.0
 */
public interface ConsistencyMarker {

    /**
     * The ResourceKey under which consistency markers are stored in a {@link Context}.
     *
     * @see Context#getResource(Context.ResourceKey)
     */
    Context.ResourceKey<ConsistencyMarker> RESOURCE_KEY = Context.ResourceKey.withLabel("consistencyMarker");

    /**
     * The consistency marker representing the start of an event stream. Effectively any event present in an event store
     * would represent a conflict with this marker.
     */
    ConsistencyMarker ORIGIN = ConsistencyMarkers.OriginConsistencyMarker.INSTANCE;

    /**
     * The consistency marker representing the end of an event stream. Effectively no event present in an event store
     * would represent a conflict with this marker.
     */
    ConsistencyMarker INFINITY = ConsistencyMarkers.InfinityConsistencyMarker.INSTANCE;

    /**
     * Returns a ConsistencyMarker that represents the lower bound of {@code this} and given {@code other} markers.
     * Effectively, this means that any events whose position in the event stream is beyond either {@code this} or the
     * {@code other} marker, will also be beyond the returned marker.
     *
     * @param other The other marker to create the lower bound for
     * @return a ConsistencyMarker that represents the lower bound of two other markers
     */
    ConsistencyMarker lowerBound(ConsistencyMarker other);

    /**
     * Returns a ConsistencyMarker that represents the upper bound of {@code this} and given {@code other} markers.
     * Effectively, this means that only events whose position in the event stream is beyond both {@code this} and the
     * {@code other} marker, will also be beyond the returned marker.
     *
     * @param other The other marker to create the upper bound for
     * @return a ConsistencyMarker that represents the upper bound of two other markers
     */
    ConsistencyMarker upperBound(ConsistencyMarker other);
}
