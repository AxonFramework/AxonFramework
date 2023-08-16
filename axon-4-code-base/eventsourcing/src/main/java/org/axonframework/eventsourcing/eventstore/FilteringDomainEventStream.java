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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.DomainEventMessage;

import java.util.function.Predicate;

/**
 * DomainEventStream implementation that filters a stream.
 * 
 * @author André Bierwolf
 * @since 3.3
 */
public class FilteringDomainEventStream implements DomainEventStream {

    private final DomainEventStream delegate;
    private final Predicate<? super DomainEventMessage<?>> filter;
    private Long lastSequenceNumber;

    /**
     * Initialize the stream, filter the given {@code stream} with the given
     * {@code filter}.
     *
     * @param delegate The stream providing the elements
     * @param filter   The filter to apply to the delegate stream
     */
    public FilteringDomainEventStream(DomainEventStream delegate, Predicate<? super DomainEventMessage<?>> filter) {
        this.delegate = delegate;
        this.filter = filter;
    }

    @Override
    public DomainEventMessage<?> peek() {
        if (!hasNext()) {
            return null;
        }
        return delegate.peek();
    }

    @Override
    public boolean hasNext() {
        // check if there is anything to read
        if (!delegate.hasNext()) {
            return false;
        }

        DomainEventMessage<?> peeked = delegate.peek();
        while (!filter.test(peeked)) {
            // consume
            delegate.next();
            if (delegate.hasNext()) {
                peeked = delegate.peek();
            } else {
                return false;
            }
        }
        return delegate.hasNext();
    }

    @Override
    public DomainEventMessage<?> next() {
        if (!hasNext()) {
            return null;
        }
        DomainEventMessage<?> next = delegate.next();
        lastSequenceNumber = next.getSequenceNumber();
        return next;
    }

    @Override
    public Long getLastSequenceNumber() {
        return lastSequenceNumber;
    }
}
