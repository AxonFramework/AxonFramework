/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.domain;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Creates a DomainEventStream that streams the contents of a list.
 *
 * @author Allard Buijze
 * @since 0.1
 */
public class SimpleDomainEventStream implements DomainEventStream {

    private final Iterator<? extends DomainEvent> iterator;
    private volatile DomainEvent peeked;

    /**
     * Initialize the event stream using the given List of DomainEvent and aggregate identifier.
     *
     * @param events the list of domain events to stream
     * @throws IllegalArgumentException if the given list is empty
     */
    public SimpleDomainEventStream(Iterable<? extends DomainEvent> events) {
        this.iterator = events.iterator();
    }

    /**
     * Initialize the event stream using the given {@link org.axonframework.domain.DomainEvent}s and aggregate
     * identifier. The aggregate identifier is initialized by reading it from the first event available. Therefore, you
     * must provide at least one event.
     *
     * @param events the list of domain events to stream
     * @throws IllegalArgumentException if no events are supplied
     */
    public SimpleDomainEventStream(DomainEvent... events) {
        this(Arrays.asList(events));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        return peeked != null || iterator.hasNext();
    }

    /**
     * {@inheritDoc}
     *
     * @throws java.util.NoSuchElementException
     *          when no items exist after the current pointer in the stream
     */
    @Override
    public DomainEvent next() {
        if (peeked != null) {
            DomainEvent returnValue = peeked;
            peeked = null;
            return returnValue;
        }
        return iterator.next();
    }

    /**
     * {@inheritDoc}
     *
     * @throws java.util.NoSuchElementException
     *          when no items exist after the current pointer in the stream
     */
    @Override
    public DomainEvent peek() {
        if (peeked != null) {
            return peeked;
        }
        peeked = iterator.next();
        return peeked;
    }
}
