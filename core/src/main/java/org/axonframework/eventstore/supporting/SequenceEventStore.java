/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.eventstore.supporting;

import org.axonframework.common.io.IOUtils;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.DomainEventStream;
import org.axonframework.eventsourcing.SimpleDomainEventStream;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.management.Criteria;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.eventstore.management.EventStoreManagement;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

/**
 * TODO: fix documentation
 *
 * Joins two {@link EventStore}s together.
 * 
 * <p>
 * First {@link EventStore} is read-only, supposed to hold the oldest events. The second {@link EventStore} is both read
 * and write. Appends are only directed to the second {@link EventStore}, supposed to hold newer events.
 * </p>
 * 
 * <p>
 * Reads are first made from the first {@link EventStore}, then from the second {@link EventStore}.
 * </p>
 * 
 * @author Knut-Olav Hoven
 */
@SuppressWarnings("rawtypes")
public class SequenceEventStore implements EventStore, EventStoreManagement {
    private final EventStore first;
    private final EventStoreManagement firstManagement;
    private final EventStore second;
    private final EventStoreManagement secondManagement;

    public SequenceEventStore(
            EventStore second,
            EventStoreManagement secondManagement,
            EventStore first,
            EventStoreManagement firstManagement) {
        this.second = second;
        this.secondManagement = secondManagement;
        this.first = first;
        this.firstManagement = firstManagement;
    }

    @Override
    public synchronized void visitEvents(EventVisitor visitor) {
        firstManagement.visitEvents(visitor);
        secondManagement.visitEvents(visitor);
    }

    @Override
    public synchronized void visitEvents(Criteria criteria, EventVisitor visitor) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public CriteriaBuilder newCriteriaBuilder() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void appendEvents(List<DomainEventMessage<?>> events) {
        second.appendEvents(events);
    }

    @Override
    public DomainEventStream readEvents(String identifier, long firstSequenceNumber,
                                        long lastSequenceNumber) {
        return new JoinedDomainEventStream(
                ignoreMissing(() -> first.readEvents(identifier, firstSequenceNumber, lastSequenceNumber)),
                second.readEvents(identifier, firstSequenceNumber, lastSequenceNumber));
    }

    @Override
    public DomainEventStream readEvents(String identifier, long firstSequenceNumber) {
        return new JoinedDomainEventStream(
                ignoreMissing(() -> first.readEvents(identifier, firstSequenceNumber)),
                second.readEvents(identifier, firstSequenceNumber));
    }

    @Override
    public synchronized DomainEventStream readEvents(String identifier) {
        return new JoinedDomainEventStream(
                ignoreMissing(() -> first.readEvents(identifier)),
                second.readEvents(identifier));
    }

    private DomainEventStream ignoreMissing(Supplier<DomainEventStream> readEventsFunction) {
        try {
            return readEventsFunction.get();
        } catch (EventStreamNotFoundException e) {
            // ignore when first eventstore have no events for requested aggregate
            return new SimpleDomainEventStream();
        }
    }

    private static final class JoinedDomainEventStream implements DomainEventStream, Closeable {
        private final DomainEventStream events1;
        private final DomainEventStream events2;

        private DomainEventMessage next;

        public JoinedDomainEventStream(
                DomainEventStream events1,
                DomainEventStream events2) {
            this.events1 = events1;
            this.events2 = events2;
            this.next = findNextItem();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public synchronized DomainEventMessage next() {
            DomainEventMessage current = next;
            next = findNextItem();
            return current;
        }

        private DomainEventMessage findNextItem() {
            if (events1.hasNext()) {
                return events1.next();
            }
            if (events2.hasNext()) {
                return events2.next();
            }
            return null;
        }

        @Override
        public DomainEventMessage peek() {
            return next;
        }

        @Override
        public void close() throws IOException {
            IOUtils.closeIfCloseable(events1);
            IOUtils.closeIfCloseable(events2);
        }
    }
}
