/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.eventsourcing;

import org.axonframework.messaging.eventhandling.DomainEventMessage;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.messaging.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.eventsourcing.snapshotting.AggregateSnapshotter;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.common.util.StubDomainEvent;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.axonframework.modelling.command.AggregateLifecycle.markDeleted;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AggregateSnapshotter}.
 *
 * @author Allard Buijze
 */
public class AggregateSnapshotterTest {

    private AggregateFactory<StubAggregate> mockAggregateFactory;

    private AggregateSnapshotter testSubject;

    @BeforeEach
    void setUp() {
        //noinspection unchecked
        mockAggregateFactory = mock(AggregateFactory.class);
        when(mockAggregateFactory.getAggregateType()).thenReturn(StubAggregate.class);
        testSubject = AggregateSnapshotter.builder()
                                          .eventStore(mock(EventStore.class))
//                                          .aggregateFactories(singletonList(mockAggregateFactory))
                                          .build();
    }

    @Test
    void createSnapshot() {
        String aggregateIdentifier = UUID.randomUUID().toString();
        DomainEventMessage firstEvent = new GenericDomainEventMessage(
                "type", aggregateIdentifier, 0L, new MessageType("event"), "Mock contents"
        );
        DomainEventStream eventStream = DomainEventStream.of(firstEvent);
        StubAggregate aggregate = new StubAggregate(aggregateIdentifier);
        when(mockAggregateFactory.createAggregateRoot(aggregateIdentifier, firstEvent)).thenReturn(aggregate);

//        DomainEventMessage snapshot =
//                testSubject.createSnapshot(StubAggregate.class, aggregateIdentifier, eventStream);
//        verify(mockAggregateFactory).createAggregateRoot(aggregateIdentifier, firstEvent);
//        assertSame(aggregate, snapshot.payload());
    }

    @Test
    @Disabled("TODO #3105")
    void createSnapshot_FirstEventLoadedIsSnapshotEvent() {
        UUID aggregateIdentifier = UUID.randomUUID();
        StubAggregate aggregate = new StubAggregate(aggregateIdentifier);

        DomainEventMessage first = new GenericDomainEventMessage(
                "type", aggregate.getIdentifier(), 0, new MessageType("snapshot"), aggregate
        );
        DomainEventMessage second = new GenericDomainEventMessage(
                "type", aggregateIdentifier.toString(), 0, new MessageType("event"), "Mock contents"
        );
        DomainEventStream eventStream = DomainEventStream.of(first, second);

        when(mockAggregateFactory.createAggregateRoot(any(), any(DomainEventMessage.class)))
                .thenAnswer(invocation -> ((DomainEventMessage) invocation.getArguments()[1]).payload());

//        DomainEventMessage snapshot =
//                testSubject.createSnapshot(StubAggregate.class, aggregateIdentifier.toString(), eventStream);
//        assertSame(aggregate, snapshot.payload(), "Snapshotter did not recognize the aggregate snapshot");

        verify(mockAggregateFactory).createAggregateRoot(any(), any(DomainEventMessage.class));
    }

    @Test
    @Disabled("TODO #3105")
    void createSnapshot_AggregateMarkedDeletedWillNotGenerateSnapshot() {
        String aggregateIdentifier = UUID.randomUUID().toString();
        DomainEventMessage firstEvent = new GenericDomainEventMessage(
                "type", aggregateIdentifier, 0L, new MessageType("event"), "Mock contents"
        );
        DomainEventMessage secondEvent = new GenericDomainEventMessage(
                "type", aggregateIdentifier, 0L, new MessageType("event"), "deleted"
        );
        DomainEventStream eventStream = DomainEventStream.of(firstEvent, secondEvent);
        StubAggregate aggregate = new StubAggregate(aggregateIdentifier);
        when(mockAggregateFactory.createAggregateRoot(aggregateIdentifier, firstEvent)).thenReturn(aggregate);

//        DomainEventMessage snapshot =
//                testSubject.createSnapshot(StubAggregate.class, aggregateIdentifier, eventStream);

        verify(mockAggregateFactory).createAggregateRoot(aggregateIdentifier, firstEvent);
//        assertNull(snapshot, "Snapshotter shouldn't have created snapshot of deleted aggregate");
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

        @SuppressWarnings("unused")
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
            if ("Mock contents".equals(event.payload().toString())) {
                apply("Another");
            }
            if ("deleted".equals(event.payload().toString())) {
                markDeleted();
            }
        }
    }
}
