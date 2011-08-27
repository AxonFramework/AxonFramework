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

package org.axonframework.domain;

import java.util.Arrays;
import java.util.Collection;
import java.util.NoSuchElementException;

/**
 * Creates a DomainEventStream that streams the contents of a list.
 *
 * @author Allard Buijze
 * @since 0.1
 */
public class SimpleDomainEventStream implements DomainEventStream {

    private static final DomainEventStream EMPTY_STREAM = new SimpleDomainEventStream();

    private int nextIndex;
    private final DomainEvent[] events;

    /**
     * Initialize the event stream using the given List of DomainEvent and aggregate identifier.
     *
     * @param events the list of domain events to stream
     * @throws IllegalArgumentException if the given list is empty
     */
    public SimpleDomainEventStream(Collection<? extends DomainEvent> events) {
        this(events.toArray(new DomainEvent[events.size()]));
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
        this.events = Arrays.copyOfRange(events, 0, events.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        return events.length > nextIndex;
    }

    /**
     * {@inheritDoc}
     *
     * @throws java.util.NoSuchElementException
     *          when no items exist after the current pointer in the stream
     */
    @Override
    public DomainEvent next() {
        if (!hasNext()) {
            throw new NoSuchElementException("Trying to peek beyond the limits of this stream.");
        }
        return events[nextIndex++];
    }

    /**
     * {@inheritDoc}
     *
     * @throws java.util.NoSuchElementException
     *          when no items exist after the current pointer in the stream
     */
    @Override
    public DomainEvent peek() {
        if (!hasNext()) {
            throw new NoSuchElementException("Trying to peek beyond the limits of this stream.");
        }
        return events[nextIndex];
    }

    /**
     * Append the contents of this stream to the given <code>collection</code>. This includes all events that have
     * already been read from the stream.
     *
     * @param collection the collection to append the events to.
     */
    public void appendTo(Collection<DomainEvent> collection) {
        collection.addAll(Arrays.asList(events));
    }

    /**
     * Creates an empty stream. For performance reasons, this method always returns the same instance.
     *
     * @return en empty DomainEventStream
     */
    public static DomainEventStream emptyStream() {
        return EMPTY_STREAM;
    }
}
