/*
 * Copyright (c) 2010-2012. Axon Framework
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
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubAggregate;
import org.axonframework.eventstore.SnapshotEventStore;
import org.junit.*;

import java.util.Arrays;
import java.util.UUID;

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
        when(mockAggregateFactory.getAggregateType()).thenReturn(EventSourcedAggregateRoot.class);
        testSubject = new AggregateSnapshotter();
        testSubject.setAggregateFactories(Arrays.<AggregateFactory<?>>asList(mockAggregateFactory));
        testSubject.setEventStore(mockEventStore);
        testSubject.setExecutor(DirectExecutor.INSTANCE);
    }

    @Test
    public void testCreateSnapshot() {
        String aggregateIdentifier = UUID.randomUUID().toString();
        DomainEventMessage firstEvent = new GenericDomainEventMessage<>(aggregateIdentifier, (long) 0,
                                                                        "Mock contents", MetaData.emptyInstance()
        );
        SimpleDomainEventStream eventStream = new SimpleDomainEventStream(firstEvent);
        EventSourcedAggregateRoot aggregate = mock(EventSourcedAggregateRoot.class);
        when(aggregate.getIdentifier()).thenReturn(aggregateIdentifier);
        when(mockAggregateFactory.createAggregate(aggregateIdentifier, firstEvent)).thenReturn(aggregate);

        DomainEventMessage snapshot = testSubject.createSnapshot(EventSourcedAggregateRoot.class,
                                                                 aggregateIdentifier, eventStream);

        verify(mockAggregateFactory).createAggregate(aggregateIdentifier, firstEvent);
        assertSame(aggregate, snapshot.getPayload());
    }

    @Test
    public void testCreateSnapshot_FirstEventLoadedIsSnapshotEvent() {
        UUID aggregateIdentifier = UUID.randomUUID();
        StubAggregate aggregate = new StubAggregate(aggregateIdentifier);
        aggregate.doSomething();
        aggregate.commitEvents();

        DomainEventMessage<StubAggregate> first =
                new GenericDomainEventMessage<>(
                        aggregate.getIdentifier(), aggregate.getVersion(), aggregate);
        DomainEventMessage secondEvent = new GenericDomainEventMessage<>(aggregateIdentifier.toString(), (long) 0,
                                                                         "Mock contents", MetaData.emptyInstance()
        );
        SimpleDomainEventStream eventStream = new SimpleDomainEventStream(first, secondEvent);

        when(mockAggregateFactory.createAggregate(any(), any(DomainEventMessage.class)))
                .thenAnswer(invocation -> ((DomainEventMessage) invocation.getArguments()[1]).getPayload());

        DomainEventMessage snapshot = testSubject.createSnapshot(EventSourcedAggregateRoot.class,
                                                                 aggregateIdentifier.toString(), eventStream);
        assertSame("Snapshotter did not recognize the aggregate snapshot", aggregate, snapshot.getPayload());

        verify(mockAggregateFactory).createAggregate(any(), any(DomainEventMessage.class));
    }
}
