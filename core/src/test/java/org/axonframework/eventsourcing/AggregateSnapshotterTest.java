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

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubAggregate;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.util.DirectExecutor;
import org.junit.*;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AggregateSnapshotterTest {

    private AggregateSnapshotter testSubject;
    private AggregateFactory mockAggregateFactory;

    @SuppressWarnings({"unchecked"})
    @Before
    public void setUp() throws Exception {
        SnapshotEventStore mockEventStore = mock(SnapshotEventStore.class);
        mockAggregateFactory = mock(AggregateFactory.class);
        when(mockAggregateFactory.getTypeIdentifier()).thenReturn("test");
        testSubject = new AggregateSnapshotter();
        testSubject.setAggregateFactories(Arrays.<AggregateFactory<?>>asList(mockAggregateFactory));
        testSubject.setEventStore(mockEventStore);
        testSubject.setExecutor(DirectExecutor.INSTANCE);
    }

    @Test
    public void testCreateSnapshot() {
        AggregateIdentifier aggregateIdentifier = new UUIDAggregateIdentifier();
        StubDomainEvent firstEvent = new StubDomainEvent(aggregateIdentifier, 0);
        SimpleDomainEventStream eventStream = new SimpleDomainEventStream(firstEvent);
        EventSourcedAggregateRoot aggregate = mock(EventSourcedAggregateRoot.class);
        when(mockAggregateFactory.createAggregate(aggregateIdentifier, firstEvent)).thenReturn(aggregate);

        AggregateSnapshot snapshot = (AggregateSnapshot) testSubject.createSnapshot("test", eventStream);

        verify(mockAggregateFactory).createAggregate(aggregateIdentifier, firstEvent);
        assertSame(aggregate, snapshot.getAggregate());
    }

    @Test
    public void testCreateSnapshot_FirstEventLoadedIsSnapshotEvent() {
        AggregateIdentifier aggregateIdentifier = new UUIDAggregateIdentifier();
        StubAggregate aggregate = new StubAggregate(aggregateIdentifier);
        aggregate.doSomething();
        aggregate.commitEvents();

        DomainEvent firstEvent = new AggregateSnapshot<StubAggregate>(aggregate);
        StubDomainEvent secondEvent = new StubDomainEvent(aggregateIdentifier, 0);
        SimpleDomainEventStream eventStream = new SimpleDomainEventStream(firstEvent, secondEvent);

        when(mockAggregateFactory.createAggregate(any(AggregateIdentifier.class), any(DomainEvent.class)))
                .thenThrow(new AssertionError("This invocation is not expected. When an aggregate snapshot is read, "
                                                      + "the aggregate should be extracted from there."));

        AggregateSnapshot snapshot = (AggregateSnapshot) testSubject.createSnapshot("test", eventStream);
        assertSame("Snapshotter did not recognize the aggregate snapshot", aggregate, snapshot.getAggregate());

        verify(mockAggregateFactory, never()).createAggregate(any(AggregateIdentifier.class), any(DomainEvent.class));
    }
}
