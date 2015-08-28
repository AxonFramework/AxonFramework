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
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.management.Criteria;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.eventstore.management.EventStoreManagement;

import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * <p>
 * Takes a backend {@link EventStore}, and functions as a filter based on a {@link Instant}.
 * </p><p>
 * Only events that are older than the provided cut-off datetime are returned to caller or handed to an {@link
 * EventVisitor}.
 * </p><p>
 * This is a read-only implementation. Appending events is not allowed.
 * </p>
 *
 * @author Knut-Olav Hoven
 */
@SuppressWarnings("rawtypes")
public class TimestampCutoffReadonlyEventStore implements EventStore, EventStoreManagement {

    private final EventStore backend;
    private final EventStoreManagement backendManagement;
    private final Instant cutoffTimestamp;

    public TimestampCutoffReadonlyEventStore(
            EventStore backend,
            EventStoreManagement backendManagement,
            Instant snapshotTimestamp) {
        this.backend = backend;
        this.backendManagement = backendManagement;
        this.cutoffTimestamp = snapshotTimestamp;
    }

    private static EventVisitor cutOffEventVisitor(final EventVisitor visitor, final Instant snapshotTimestamp) {
        return domainEvent -> {
            if (domainEvent.getTimestamp().isBefore(snapshotTimestamp)) {
                visitor.doWithEvent(domainEvent);
            }
        };
    }

    @Override
    public void visitEvents(EventVisitor visitor) {
        backendManagement.visitEvents(cutOffEventVisitor(visitor, cutoffTimestamp));
    }

    @Override
    public void visitEvents(Criteria criteria, EventVisitor visitor) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public CriteriaBuilder newCriteriaBuilder() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void appendEvents(List<DomainEventMessage<?>> events) {
        throw new IllegalStateException("Not allowed to append events to " + getClass() + ".");
    }

    @Override
    public DomainEventStream readEvents(String identifier, long firstSequenceNumber,
                                        long lastSequenceNumber) {
        return cutOff(backend.readEvents(identifier, firstSequenceNumber, lastSequenceNumber));
    }

    @Override
    public DomainEventStream readEvents(String identifier, long firstSequenceNumber) {
        return cutOff(backend.readEvents(identifier, firstSequenceNumber));
    }

    @Override
    public DomainEventStream readEvents(String identifier) {
        return cutOff(backend.readEvents(identifier));
    }

    private DomainEventStream cutOff(DomainEventStream events) {
        return new TimestampCutOffDomainEventStream(events, cutoffTimestamp);
    }

    private static final class TimestampCutOffDomainEventStream implements DomainEventStream, Closeable {

        private final Instant timeStampCutOff;
        private final DomainEventStream events;

        private DomainEventMessage next;

        public TimestampCutOffDomainEventStream(DomainEventStream events, Instant timeStampCutOff) {
            this.events = events;
            this.timeStampCutOff = timeStampCutOff;
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
            DomainEventMessage retval = null;
            while (retval == null && events.hasNext()) {
                final DomainEventMessage candidate = events.next();
                if (candidate.getTimestamp().isBefore(timeStampCutOff)) {
                    retval = candidate;
                } else {
                    retval = null;
                }
            }
            return retval;
        }

        @Override
        public DomainEventMessage peek() {
            return next;
        }

        @Override
        public void close() throws IOException {
            IOUtils.closeIfCloseable(events);
        }
    }
}
