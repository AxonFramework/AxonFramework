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

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.AggregateIdentifierFactory;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.util.SynchronousTaskExecutor;
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
        testSubject.setExecutor(SynchronousTaskExecutor.INSTANCE);
    }

    @Test
    public void testCreateSnapshot() {
        AggregateIdentifier aggregateIdentifier = AggregateIdentifierFactory.randomIdentifier();
        StubDomainEvent firstEvent = new StubDomainEvent(aggregateIdentifier, 0);
        SimpleDomainEventStream eventStream = new SimpleDomainEventStream(firstEvent);
        EventSourcedAggregateRoot aggregate = mock(EventSourcedAggregateRoot.class);
        when(mockAggregateFactory.createAggregate(aggregateIdentifier, firstEvent)).thenReturn(aggregate);

        AggregateSnapshot snapshot = (AggregateSnapshot) testSubject.createSnapshot("test", eventStream);

        verify(mockAggregateFactory).createAggregate(aggregateIdentifier, firstEvent);
        assertSame(aggregate, snapshot.getAggregate());
    }
}
