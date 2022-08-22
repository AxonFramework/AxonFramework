/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.utils.StubDomainEvent;
import org.axonframework.messaging.MetaData;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static java.util.Collections.singletonList;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.axonframework.modelling.command.AggregateLifecycle.markDeleted;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AggregateSnapshotterTest {

    private AggregateSnapshotter testSubject;
    private AggregateFactory mockAggregateFactory;

    @BeforeEach
    @SuppressWarnings({"unchecked"})
    void setUp() {
        mockAggregateFactory = mock(AggregateFactory.class);
        when(mockAggregateFactory.getAggregateType()).thenReturn(StubAggregate.class);
        testSubject = AggregateSnapshotter.builder()
                                          .eventStore(mock(EventStore.class))
                                          .aggregateFactories(singletonList(mockAggregateFactory))
                                          .build();
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void testCreateSnapshot() {
        String aggregateIdentifier = UUID.randomUUID().toString();
        DomainEventMessage firstEvent = new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 0,
                                                                        "Mock contents", MetaData.emptyInstance());
        DomainEventStream eventStream = DomainEventStream.of(firstEvent);
        StubAggregate aggregate = new StubAggregate(aggregateIdentifier);
        when(mockAggregateFactory.createAggregateRoot(aggregateIdentifier, firstEvent)).thenReturn(aggregate);

        DomainEventMessage snapshot = testSubject.createSnapshot(StubAggregate.class,
                                                                 aggregateIdentifier, eventStream);

        verify(mockAggregateFactory).createAggregateRoot(aggregateIdentifier, firstEvent);
        assertSame(aggregate, snapshot.getPayload());
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void testCreateSnapshot_FirstEventLoadedIsSnapshotEvent() {
        UUID aggregateIdentifier = UUID.randomUUID();
        StubAggregate aggregate = new StubAggregate(aggregateIdentifier);

        DomainEventMessage<StubAggregate> first = new GenericDomainEventMessage<>("type", aggregate.getIdentifier(), 0,
                                                                                  aggregate);
        DomainEventMessage second = new GenericDomainEventMessage<>(
                "type", aggregateIdentifier.toString(), 0, "Mock contents", MetaData.emptyInstance());
        DomainEventStream eventStream = DomainEventStream.of(first, second);

        when(mockAggregateFactory.createAggregateRoot(any(), any(DomainEventMessage.class)))
                .thenAnswer(invocation -> ((DomainEventMessage) invocation.getArguments()[1]).getPayload());

        DomainEventMessage snapshot = testSubject.createSnapshot(StubAggregate.class,
                                                                 aggregateIdentifier.toString(), eventStream);
        assertSame(aggregate, snapshot.getPayload(), "Snapshotter did not recognize the aggregate snapshot");

        verify(mockAggregateFactory).createAggregateRoot(any(), any(DomainEventMessage.class));
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void testCreateSnapshot_AggregateMarkedDeletedWillNotGenerateSnapshot() {
        String aggregateIdentifier = UUID.randomUUID().toString();
        DomainEventMessage firstEvent = new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 0,
                                                                        "Mock contents", MetaData.emptyInstance());
        DomainEventMessage secondEvent = new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 0,
                                                                         "deleted", MetaData.emptyInstance());
        DomainEventStream eventStream = DomainEventStream.of(firstEvent, secondEvent);
        StubAggregate aggregate = new StubAggregate(aggregateIdentifier);
        when(mockAggregateFactory.createAggregateRoot(aggregateIdentifier, firstEvent)).thenReturn(aggregate);

        DomainEventMessage snapshot = testSubject.createSnapshot(StubAggregate.class,
                                                                 aggregateIdentifier, eventStream);

        verify(mockAggregateFactory).createAggregateRoot(aggregateIdentifier, firstEvent);
        assertNull(snapshot, "Snapshotter shouldn't have created snapshot of deleted aggregate");
    }

    public static class StubAggregate {

        @AggregateIdentifier
        private String identifier;

        public StubAggregate() {
            identifier = UUID.randomUUID().toString();
        }

        public StubAggregate(Object identifier) {
            this.identifier = identifier.toString();
        }

        public void doSomething() {
            apply(new StubDomainEvent());
        }

        public String getIdentifier() {
            return identifier;
        }

        @EventSourcingHandler
        protected void handle(EventMessage event) {
            identifier = ((DomainEventMessage) event).getAggregateIdentifier();
            // See Issue #
            if ("Mock contents".equals(event.getPayload().toString())) {
                apply("Another");
            }
            if ("deleted".equals(event.getPayload().toString())) {
                markDeleted();
            }
        }
    }
}
