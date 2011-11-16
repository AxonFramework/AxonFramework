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

import org.axonframework.common.DirectExecutor;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.axonframework.eventstore.SnapshotEventStore;
import org.hamcrest.Matcher;
import org.junit.*;
import org.mockito.*;

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
            protected DomainEventMessage createSnapshot(String typeIdentifier, DomainEventStream eventStream) {
                AggregateIdentifier aggregateIdentifier = eventStream.peek().getAggregateIdentifier();
                long lastIdentifier = getLastIdentifierFrom(eventStream);
                if (lastIdentifier <= 0) {
                    return null;
                }
                return new GenericDomainEventMessage<String>(aggregateIdentifier, lastIdentifier,
                                                             "Mock contents", MetaData.emptyInstance());
            }
        };
        testSubject.setEventStore(mockEventStore);
        testSubject.setExecutor(DirectExecutor.INSTANCE);
    }

    @Test
    public void testScheduleSnapshot() {
        AggregateIdentifier aggregateIdentifier = new UUIDAggregateIdentifier();
        when(mockEventStore.readEvents("test", aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(
                        new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 0,
                                                              "Mock contents", MetaData.emptyInstance()),
                        new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 1,
                                                              "Mock contents", MetaData.emptyInstance())));
        testSubject.scheduleSnapshot("test", aggregateIdentifier);
        verify(mockEventStore).appendSnapshotEvent(eq("test"), argThat(event(aggregateIdentifier, 1)));
    }

    @Test
    public void testScheduleSnapshot_SnapshotIsNull() {
        AggregateIdentifier aggregateIdentifier = new UUIDAggregateIdentifier();
        when(mockEventStore.readEvents("test", aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(
                        new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 0,
                                                              "Mock contents", MetaData.emptyInstance())));
        testSubject.scheduleSnapshot("test", aggregateIdentifier);
        verify(mockEventStore, never()).appendSnapshotEvent(any(String.class), any(DomainEventMessage.class));
    }

    private Matcher<DomainEventMessage> event(final AggregateIdentifier aggregateIdentifier, final long i) {
        return new ArgumentMatcher<DomainEventMessage>() {
            @Override
            public boolean matches(Object argument) {
                if (!(argument instanceof DomainEventMessage)) {
                    return false;
                }
                DomainEventMessage event = (DomainEventMessage) argument;
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
