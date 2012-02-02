/*
 * Copyright (c) 2010-2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing;

import org.axonframework.domain.DomainEventMessage;

/**
 * Interface describing objects capable of creating instances of aggregates to be initialized with an event stream.
 *
 * @param <T> The type of aggregate this factory creates
 * @author Allard Buijze
 * @since 0.6
 */
public interface AggregateFactory<T extends EventSourcedAggregateRoot> {

    /**
     * Instantiate the aggregate using the given aggregate identifier and first event. The first event of the event
     * stream is passed to allow the factory to identify the actual implementation type of the aggregate to create. The
     * first event can be either the event that created the aggregate or, when using event sourcing, a snapshot event.
     * In either case, the event should be designed, such that these events contain enough information to deduct the
     * actual aggregate type.
     *
     * @param aggregateIdentifier the aggregate identifier of the aggregate to instantiate
     * @param firstEvent          The first event in the event stream. This is either the event generated during
     *                            creation of the aggregate, or a snapshot event
     * @return an aggregate ready for initialization using a DomainEventStream.
     */
    T createAggregate(Object aggregateIdentifier, DomainEventMessage firstEvent);

    /**
     * Returns the type identifier for this aggregate factory. The type identifier is used by the EventStore to
     * organize data related to the same type of aggregate.
     * <p/>
     * Tip: in most cases, the simple class name would be a good start.
     *
     * @return the type identifier of the aggregates this repository stores
     */
    String getTypeIdentifier();
}
