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

package org.axonframework.eventsourcing;

import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.util.SynchronousTaskExecutor;
import org.hamcrest.Matcher;
import org.junit.*;
import org.mockito.*;

import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AbstractSnapshotterTest {

    private AbstractSnapshotter testSubject;
    private SnapshotEventStore mockEventStore;

    @Before
    public void setUp() throws Exception {
        mockEventStore = mock(SnapshotEventStore.class);
        testSubject = new AbstractSnapshotter() {
            @Override
            protected DomainEvent createSnapshot(String typeIdentifier, DomainEventStream eventStream) {
                UUID aggregateIdentifier = eventStream.peek().getAggregateIdentifier();
                long lastIdentifier = getLastIdentifierFrom(eventStream);
                if (lastIdentifier <= 0) {
                    return null;
                }
                return new StubDomainEvent(aggregateIdentifier, lastIdentifier);

            }
        };
        testSubject.setEventStore(mockEventStore);
        testSubject.setExecutor(SynchronousTaskExecutor.INSTANCE);
    }

    @Test
    public void testScheduleSnapshot() {
        UUID aggregateIdentifier = UUID.randomUUID();
        when(mockEventStore.readEvents("test", aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(
                        new StubDomainEvent(aggregateIdentifier, 0),
                        new StubDomainEvent(aggregateIdentifier, 1)));
        testSubject.scheduleSnapshot("test", aggregateIdentifier);
        verify(mockEventStore).appendSnapshotEvent(eq("test"), argThat(event(aggregateIdentifier, 1)));
    }

    @Test
    public void testScheduleSnapshot_SnapshotIsNull() {
        UUID aggregateIdentifier = UUID.randomUUID();
        when(mockEventStore.readEvents("test", aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(
                        new StubDomainEvent(aggregateIdentifier, 0)));
        testSubject.scheduleSnapshot("test", aggregateIdentifier);
        verify(mockEventStore, never()).appendSnapshotEvent(any(String.class), any(DomainEvent.class));
    }

    private Matcher<DomainEvent> event(final UUID aggregateIdentifier, final long i) {
        return new ArgumentMatcher<DomainEvent>() {
            @Override
            public boolean matches(Object argument) {
                if (!(argument instanceof DomainEvent)) {
                    return false;
                }
                DomainEvent event = (DomainEvent) argument;
                return aggregateIdentifier.equals(event.getAggregateIdentifier())
                        && event.getSequenceNumber() == i;
            }
        };
    }

    private long getLastIdentifierFrom(DomainEventStream eventStream) {
        long lastSequenceNumber = -1;
        while (eventStream.hasNext()) {
            lastSequenceNumber = eventStream.next().getSequenceNumber();
        }
        return lastSequenceNumber;
    }
}
