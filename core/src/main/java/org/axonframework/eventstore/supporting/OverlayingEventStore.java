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

import java.io.Closeable;
import java.io.IOException;

import org.axonframework.common.io.IOUtils;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.management.Criteria;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.eventstore.management.EventStoreManagement;

/**
 * Joins two {@link EventStore}s together.
 * 
 * <p>
 * Backend is read-only. Overlay is both read and write. Appends are only directed to the overlay {@link EventStore}.
 * </p>
 * 
 * <p>
 * Reads are first done from the backend {@link EventStore}, then from the overlay {@link EventStore}.
 * </p>
 * 
 * @author Knut-Olav Hoven
 */
@SuppressWarnings("rawtypes")
public class OverlayingEventStore implements EventStore, EventStoreManagement {
    private final EventStore backend;
    private final EventStoreManagement backendManagement;
    private final EventStore overlay;
    private final EventStoreManagement overlayManagement;

    public OverlayingEventStore(
            EventStore overlay,
            EventStoreManagement overlayManagement,
            EventStore backend,
            EventStoreManagement backendManagement) {
        this.overlay = overlay;
        this.overlayManagement = overlayManagement;
        this.backend = backend;
        this.backendManagement = backendManagement;
    }

    @Override
    public synchronized void visitEvents(EventVisitor visitor) {
        backendManagement.visitEvents(visitor);
        overlayManagement.visitEvents(visitor);
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
    public synchronized void appendEvents(String type, DomainEventStream events) {
        overlay.appendEvents(type, events);
    }

    @Override
    public synchronized DomainEventStream readEvents(String type, Object identifier) {
        return new JoinedDomainEventStream(
                backend.readEvents(type, identifier),
                overlay.readEvents(type, identifier));
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
