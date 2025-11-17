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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.eventhandling.DomainEventMessage;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.modelling.command.AggregateRoot;
import org.axonframework.modelling.command.DefaultRepositorySpanFactory;
import org.axonframework.messaging.tracing.TestSpanFactory;
import org.axonframework.common.util.StubDomainEvent;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link LegacyEventSourcingRepository}.
 *
 * @author Allard Buijze
 */
@Disabled("TODO #3486")
class LegacyEventSourcingRepositoryTest {

    private EventStore mockEventStore;
    private LegacyEventSourcingRepository<TestAggregate> testSubject;
    private LegacyUnitOfWork<?> unitOfWork;
    private StubAggregateFactory stubAggregateFactory;
    //    private SnapshotTriggerDefinition triggerDefinition;
//    private SnapshotTrigger snapshotTrigger;
    private TestSpanFactory testSpanFactory;

    @BeforeEach
    void setUp() {
        mockEventStore = mock(EventStore.class);
        stubAggregateFactory = new StubAggregateFactory();
//        snapshotTrigger = mock(SnapshotTrigger.class);
//        triggerDefinition = mock(SnapshotTriggerDefinition.class);
        testSpanFactory = new TestSpanFactory();
//        when(triggerDefinition.prepareTrigger(any())).thenReturn(snapshotTrigger);
        testSubject = LegacyEventSourcingRepository.builder(TestAggregate.class)
                                                   .aggregateFactory(stubAggregateFactory)
                                                   .eventStore(mockEventStore)
//                                                   .snapshotTriggerDefinition(triggerDefinition)
                                                   .filterByAggregateType()
                                                   .spanFactory(DefaultRepositorySpanFactory.builder()
                                                                                            .spanFactory(testSpanFactory)
                                                                                            .build())
                                                   .build();
        unitOfWork = LegacyDefaultUnitOfWork.startAndGet(
                new GenericMessage(new MessageType("message"), "test")
        );
    }

    @AfterEach
    void tearDown() {
        if (unitOfWork.isActive()) {
            unitOfWork.rollback();
        }
    }

    @Test
    void loadAndSaveAggregate() {
        String identifier = UUID.randomUUID().toString();
        DomainEventMessage event1 = new GenericDomainEventMessage(
                "type", identifier, 1L, new MessageType("event"), "Mock contents"
        );
        DomainEventMessage event2 = new GenericDomainEventMessage(
                "type", identifier, 2L, new MessageType("event"), "Mock contents"
        );
//        when(mockEventStore.readEvents(identifier)).thenReturn(DomainEventStream.of(event1, event2));

        Aggregate<TestAggregate> aggregate = testSubject.load(identifier);

        assertEquals(2, aggregate.invoke(TestAggregate::getHandledEvents).size());
        assertSame(event1, aggregate.invoke(TestAggregate::getHandledEvents).get(0));
        assertSame(event2, aggregate.invoke(TestAggregate::getHandledEvents).get(1));

        assertEquals(0, aggregate.invoke(TestAggregate::getLiveEvents).size());

        // now the aggregate is loaded (and hopefully correctly locked)
        StubDomainEvent event3 = new StubDomainEvent();

        aggregate.execute(r -> r.apply(event3));

        CurrentUnitOfWork.commit();

//        verify(mockEventStore, times(1)).publish((EventMessage) any());
        assertEquals(1, aggregate.invoke(TestAggregate::getLiveEvents).size());
        assertSame(event3, aggregate.invoke(TestAggregate::getLiveEvents).get(0).payload());
    }

    @Test
    void loadAndSaveAggregateIsTracedCorrectly() {
        String identifier = UUID.randomUUID().toString();
        DomainEventMessage event1 = new GenericDomainEventMessage(
                "type", identifier, 1L, new MessageType("event"), "Mock contents"
        );
        DomainEventMessage event2 = new GenericDomainEventMessage(
                "type", identifier, 2L, new MessageType("event"), "Mock contents"
        );
//        when(mockEventStore.readEvents(identifier)).thenAnswer(invocation -> {
//            testSpanFactory.verifySpanActive("Repository.load");
//            testSpanFactory.verifySpanCompleted("Repository.obtainLock");
//            testSpanFactory.verifyNoSpan("Repository.initializeState(type)");
//            return DomainEventStream.of(event1, event2);
//        });

        Aggregate<TestAggregate> aggregate = testSubject.load(identifier);
        testSpanFactory.verifySpanCompleted("Repository.load");
        testSpanFactory.verifySpanHasAttributeValue("Repository.load", "axon.aggregateId", identifier);
        testSpanFactory.verifySpanCompleted("Repository.obtainLock");
        testSpanFactory.verifySpanCompleted("Repository.initializeState(type)");

        // now the aggregate is loaded (and hopefully correctly locked)
        StubDomainEvent event3 = new StubDomainEvent();

        aggregate.execute(r -> r.apply(event3));

        CurrentUnitOfWork.commit();

        testSpanFactory.verifySpanHasType("Repository.load", TestSpanFactory.TestSpanType.INTERNAL);
        testSpanFactory.verifySpanHasType("Repository.obtainLock", TestSpanFactory.TestSpanType.INTERNAL);
        testSpanFactory.verifySpanHasType("Repository.initializeState(type)", TestSpanFactory.TestSpanType.INTERNAL);
    }

    @Test
    void filterEventsByType() {
        String identifier = UUID.randomUUID().toString();
        DomainEventMessage event1 = new GenericDomainEventMessage(
                "type", identifier, 1L, new MessageType("event"), "Mock contents"
        );
        DomainEventMessage event2 = new GenericDomainEventMessage(
                "otherType", identifier, 1L, new MessageType("event"), "Other contents"
        );
//        when(mockEventStore.readEvents(identifier)).thenReturn(DomainEventStream.of(event1, event2));

        Aggregate<TestAggregate> aggregate = testSubject.load(identifier);

        assertEquals(1, aggregate.invoke(TestAggregate::getHandledEvents).size());
        assertSame(event1, aggregate.invoke(TestAggregate::getHandledEvents).get(0));

        assertEquals(0, aggregate.invoke(TestAggregate::getLiveEvents).size());
    }

    @Test
    void load_FirstEventIsSnapshot() {
        String identifier = UUID.randomUUID().toString();
        TestAggregate aggregate = new TestAggregate(identifier);
        DomainEventMessage testSnapshot = new GenericDomainEventMessage(
                "type", identifier, 10, new MessageType("snapshot"), aggregate
        );
//        when(mockEventStore.readEvents(identifier)).thenReturn(DomainEventStream.of(testSnapshot));
        assertSame(aggregate, testSubject.load(identifier).getWrappedAggregate().getAggregateRoot());
    }

    @Test
    void loadEventsWithSnapshotter() {
        String identifier = UUID.randomUUID().toString();
//        when(mockEventStore.readEvents(identifier)).thenReturn(DomainEventStream.of(
//                new GenericDomainEventMessage(
//                        "type", identifier, 1L, new MessageType("event"), "Mock contents"
//                ),
//                new GenericDomainEventMessage(
//                        "type", identifier, 2L, new MessageType("event"), "Mock contents"
//                ),
//                new GenericDomainEventMessage(
//                        "type", identifier, 3L, new MessageType("event"), "Mock contents"
//                )
//        ));
        Aggregate<TestAggregate> aggregate = testSubject.load(identifier);
        aggregate.execute(r -> r.apply(new StubDomainEvent()));
        aggregate.execute(r -> r.apply(new StubDomainEvent()));

//        InOrder inOrder = Mockito.inOrder(triggerDefinition, snapshotTrigger);
//        inOrder.verify(triggerDefinition).prepareTrigger(stubAggregateFactory.getAggregateType());
//        inOrder.verify(snapshotTrigger, times(3)).eventHandled(any());
//        inOrder.verify(snapshotTrigger).initializationFinished();
//        inOrder.verify(snapshotTrigger, times(2)).eventHandled(any());
    }

    @Test
    void buildWithNullSubtypesThrowsAxonConfigurationException() {
        LegacyEventSourcingRepository.Builder<TestAggregate> builderTestSubject =
                LegacyEventSourcingRepository.builder(TestAggregate.class)
                                             .eventStore(mockEventStore);

        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.subtypes(null));
    }

    @Test
    void buildWithNullSubtypeThrowsAxonConfigurationException() {
        LegacyEventSourcingRepository.Builder<TestAggregate> builderTestSubject =
                LegacyEventSourcingRepository.builder(TestAggregate.class)
                                             .eventStore(mockEventStore);

        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.subtype(null));
    }

    private static class StubAggregateFactory extends AbstractAggregateFactory<TestAggregate> {

        public StubAggregateFactory() {
            super(TestAggregate.class);
        }

        @Override
        public TestAggregate doCreateAggregate(String aggregateIdentifier, DomainEventMessage firstEvent) {
            return new TestAggregate(aggregateIdentifier);
        }

        @Override
        public Class<TestAggregate> getAggregateType() {
            return TestAggregate.class;
        }
    }

    @AggregateRoot(type = "type")
    private static class TestAggregate {

        private final List<EventMessage> handledEvents = new ArrayList<>();
        private final List<EventMessage> liveEvents = new ArrayList<>();

        @AggregateIdentifier
        private String identifier;

        private TestAggregate(String identifier) {
            this.identifier = identifier;
        }

        public void apply(Object eventPayload) {
            AggregateLifecycle.apply(eventPayload);
        }

        public void changeState() {
            AggregateLifecycle.apply("Test more");
        }

        @EventSourcingHandler
        protected void handle(EventMessage event) {
            identifier = ((DomainEventMessage) event).getAggregateIdentifier();
            handledEvents.add(event);
            if (AggregateLifecycle.isLive()) {
                liveEvents.add(event);
            }
        }

        public List<EventMessage> getHandledEvents() {
            return handledEvents;
        }

        public List<EventMessage> getLiveEvents() {
            return liveEvents;
        }

        public String getIdentifier() {
            return identifier;
        }
    }
}
