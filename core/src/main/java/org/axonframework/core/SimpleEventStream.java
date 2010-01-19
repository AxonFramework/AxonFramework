/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Creates an EventStream that streams the contents of a list.
 *
 * @author Allard Buijze
 * @since 0.1
 */
public class SimpleEventStream implements EventStream {

    private volatile DomainEvent nextEvent;
    private final Iterator<DomainEvent> iterator;
    private final UUID identifier;

    /**
     * Initialize the event stream using the given List of DomainEvent and aggregate identifier. The List may be empty.
     *
     * @param domainEvents        the list of domain events to stream
     * @param aggregateIdentifier the aggregate identifier that the events apply to
     */
    public SimpleEventStream(List<DomainEvent> domainEvents, UUID aggregateIdentifier) {
        this.iterator = domainEvents.iterator();
        if (iterator.hasNext()) {
            nextEvent = iterator.next();
        }
        identifier = aggregateIdentifier;
    }

    /**
     * Initialize the event stream using the given List of DomainEvent and aggregate identifier. The aggregate
     * identifier is initialized by reading it from the first event available. Therefore, you must ensure that there is
     * at least one event in the provided list.
     *
     * @param domainEvents the list of domain events to stream
     * @throws IllegalArgumentException if the given list is empty
     */
    public SimpleEventStream(List<DomainEvent> domainEvents) {
        this.iterator = domainEvents.iterator();
        if (iterator.hasNext()) {
            nextEvent = iterator.next();
            identifier = nextEvent.getAggregateIdentifier();
        } else {
            throw new IllegalArgumentException("Must provide at least one event");
        }
    }

    /**
     * Initialize the event stream using the given {@link org.axonframework.core.DomainEvent}s and aggregate identifier.
     * The aggregate identifier is initialized by reading it from the first event available. Therefore, you must provide
     * at least one event.
     *
     * @param events the list of domain events to stream
     * @throws IllegalArgumentException if no events are supplied
     */
    public SimpleEventStream(DomainEvent... events) {
        this(Arrays.asList(events));
    }

    /**
     * Returns the aggregate identifier that the events in this stream apply to. Will never return null.
     *
     * @return the aggregate identifier of the events in this stream
     */
    @Override
    public UUID getAggregateIdentifier() {
        return identifier;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        return nextEvent != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DomainEvent next() {
        DomainEvent next = nextEvent;
        if (iterator.hasNext()) {
            nextEvent = iterator.next();
        } else {
            nextEvent = null;
        }
        return next;
    }
}
