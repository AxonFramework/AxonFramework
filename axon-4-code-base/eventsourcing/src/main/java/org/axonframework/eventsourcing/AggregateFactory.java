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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.DomainEventMessage;

/**
 * Interface describing objects capable of creating instances of aggregates to be initialized with an event stream.
 * <p/>
 * Implementation note: to make sure an implementation handles snapshot events correctly, consider extending {@link
 * AbstractAggregateFactory}.
 *
 * @param <T> The type of aggregate this factory creates
 * @author Allard Buijze
 * @since 0.6
 */
public interface AggregateFactory<T> {

    /**
     * Instantiate the aggregate root using the given aggregate identifier and first event. The first event of the
     * event stream is passed to allow the factory to identify the actual implementation type of the aggregate to
     * create. The first event can be either the event that created the aggregate or, when using event sourcing, a
     * snapshot event. In either case, the event should be designed, such that these events contain enough information
     * to deduct the actual aggregate type.
     *
     * @param aggregateIdentifier the aggregate identifier of the aggregate to instantiate
     * @param firstEvent          The first event in the event stream. This is either the event generated during
     *                            creation of the aggregate, or a snapshot event
     * @return an aggregate root ready for initialization using a DomainEventStream.
     */
    T createAggregateRoot(String aggregateIdentifier, DomainEventMessage<?> firstEvent);

    /**
     * Returns the type of aggregate this factory creates. All instances created by this factory must be an
     * {@code instanceOf} this type.
     *
     * @return The type of aggregate created by this factory
     */
    Class<T> getAggregateType();
}
