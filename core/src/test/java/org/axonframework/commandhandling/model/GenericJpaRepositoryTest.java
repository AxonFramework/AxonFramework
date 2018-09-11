/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.commandhandling.model;

import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.junit.*;
import org.mockito.*;
import org.mockito.internal.stubbing.answers.*;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.LockModeType;
import javax.persistence.Version;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvent;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class GenericJpaRepositoryTest {

    private EntityManager mockEntityManager;
    private GenericJpaRepository<StubJpaAggregate> testSubject;
    private String aggregateId;
    private StubJpaAggregate aggregate;
    private Function<String, ?> identifierConverter;
    private EventBus eventBus;

    @Before
    public void setUp() {
        mockEntityManager = mock(EntityManager.class);
        //noinspection unchecked
        identifierConverter = mock(Function.class);
        eventBus = new SimpleEventBus();
        when(identifierConverter.apply(anyString())).thenAnswer(i -> i.getArguments()[0]);
        testSubject = GenericJpaRepository.<StubJpaAggregate>builder()
                .aggregateType(StubJpaAggregate.class)
                .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                .eventBus(eventBus)
                .identifierConverter(identifierConverter)
                .build();
        DefaultUnitOfWork.startAndGet(null);
        aggregateId = "123";
        aggregate = new StubJpaAggregate(aggregateId);
        when(mockEntityManager.find(eq(StubJpaAggregate.class), eq("123"), any(LockModeType.class)))
                .thenReturn(aggregate);
    }

    @After
    public void cleanUp() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testAggregateStoredBeforeEventsPublished() throws Exception {
        //noinspection unchecked
        Consumer<List<? extends EventMessage<?>>> mockConsumer = mock(Consumer.class);
        eventBus.subscribe(mockConsumer);

        testSubject.newInstance(() -> new StubJpaAggregate("test", "payload1", "payload2"));
        CurrentUnitOfWork.commit();

        InOrder inOrder = inOrder(mockEntityManager, mockConsumer);
        inOrder.verify(mockEntityManager).persist(any());
        inOrder.verify(mockConsumer).accept(anyList());
    }

    @Test
    public void testLoadAggregate() {
        Aggregate<StubJpaAggregate> actualResult = testSubject.load(aggregateId);
        assertSame(aggregate, actualResult.invoke(Function.identity()));
    }

    @Test
    public void testLoadAggregateWithConverter() {
        when(identifierConverter.apply("original")).thenAnswer(new Returns(aggregateId));
        Aggregate<StubJpaAggregate> actualResult = testSubject.load("original");
        assertSame(aggregate, actualResult.invoke(Function.identity()));
    }

    @Test
    public void testAggregateCreatesSequenceNumbersForNewAggregatesWhenUsingEventStore() throws Exception {
        EventStore mockEventStore = new EmbeddedEventStore(new InMemoryEventStorageEngine());
        testSubject = GenericJpaRepository.<StubJpaAggregate>builder()
                .aggregateType(StubJpaAggregate.class)
                .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                .eventBus(mockEventStore)
                .identifierConverter(identifierConverter)
                .build();

        DefaultUnitOfWork.startAndGet(null).executeWithResult(() -> {
            Aggregate<StubJpaAggregate> agg = testSubject.newInstance(() -> new StubJpaAggregate("id",
                                                                                                 "test1",
                                                                                                 "test2"));
            agg.execute(e -> e.doSomething("test3"));
            return null;
        });
        CurrentUnitOfWork.commit();

        DomainEventStream events = mockEventStore.readEvents("id");
        assertTrue(events.hasNext());
        DomainEventMessage<?> next1 = events.next();
        assertEquals("test1", next1.getPayload());
        assertEquals(0, next1.getSequenceNumber());
        assertEquals("id", next1.getAggregateIdentifier());

        DomainEventMessage<?> next2 = events.next();
        assertEquals("test2", next2.getPayload());
        assertEquals(1, next2.getSequenceNumber());
        assertEquals("id", next2.getAggregateIdentifier());

        DomainEventMessage<?> next3 = events.next();
        assertEquals("test3", next3.getPayload());
        assertEquals(2, next3.getSequenceNumber());
        assertEquals("id", next3.getAggregateIdentifier());
    }

    @Test
    public void testAggregateCreatesSequenceNumbersForExistingAggregatesWithEventsInEventStore() throws Exception {
        EventStore mockEventStore = new EmbeddedEventStore(new InMemoryEventStorageEngine());
        mockEventStore.publish(createEvent("123", 0), createEvent("123", 1));
        testSubject = GenericJpaRepository.<StubJpaAggregate>builder()
                .aggregateType(StubJpaAggregate.class)
                .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                .eventBus(mockEventStore)
                .identifierConverter(identifierConverter)
                .build();

        DefaultUnitOfWork.startAndGet(null).executeWithResult(() -> {
            Aggregate<StubJpaAggregate> agg = testSubject.load("123");
            agg.execute(e -> e.doSomething("test2"));
            return null;
        });

        CurrentUnitOfWork.commit();

        DomainEventStream events = mockEventStore.readEvents("123");
        // first the two we added ourselves
        assertNotNull(events.next());
        assertNotNull(events.next());

        assertTrue(events.hasNext());
        DomainEventMessage<?> next = events.next();
        assertEquals("test2", next.getPayload());
        assertEquals(2, next.getSequenceNumber());
    }

    @Test
    public void testAggregateDoesNotCreateSequenceNumbersWhenNoEventsInStore() throws Exception {
        EventStore mockEventStore = new EmbeddedEventStore(new InMemoryEventStorageEngine());
        testSubject = GenericJpaRepository.<StubJpaAggregate>builder()
                .aggregateType(StubJpaAggregate.class)
                .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                .eventBus(mockEventStore)
                .identifierConverter(identifierConverter)
                .build();

        DefaultUnitOfWork.startAndGet(null).executeWithResult(() -> {
            Aggregate<StubJpaAggregate> agg = testSubject.load(aggregateId);
            agg.execute(e -> e.doSomething("test2"));
            return null;
        });

        CurrentUnitOfWork.commit();

        DomainEventStream events = mockEventStore.readEvents(aggregateId);
        assertFalse(events.hasNext());
        assertTrue(mockEventStore.openStream(null).hasNextAvailable());
        assertEquals("test2", mockEventStore.openStream(null).nextAvailable().getPayload());
    }

    @Test
    public void testLoadAggregate_NotFound() {
        String aggregateIdentifier = UUID.randomUUID().toString();
        try {
            testSubject.load(aggregateIdentifier);
            fail("Expected AggregateNotFoundException");
        } catch (AggregateNotFoundException e) {
            assertEquals(aggregateIdentifier, e.getAggregateIdentifier());
        }
    }

    @Test
    public void testLoadAggregate_WrongVersion() {
        try {
            testSubject.load(aggregateId, 2L);
            fail("Expected ConflictingAggregateVersionException");
        } catch (ConflictingAggregateVersionException e) {
            assertEquals(2L, e.getExpectedVersion());
            assertEquals(0L, e.getActualVersion());
        }
    }

    @Test
    public void testPersistAggregate_DefaultFlushMode() {
        testSubject.doSave(testSubject.load(aggregateId));
        verify(mockEntityManager).persist(aggregate);
        verify(mockEntityManager).flush();
    }

    @Test
    public void testPersistAggregate_ExplicitFlushModeOn() {
        testSubject.setForceFlushOnSave(true);
        testSubject.doSave(testSubject.load(aggregateId));
        verify(mockEntityManager).persist(aggregate);
        verify(mockEntityManager).flush();
    }

    @Test
    public void testPersistAggregate_ExplicitFlushModeOff() {
        testSubject.setForceFlushOnSave(false);
        testSubject.doSave(testSubject.load(aggregateId));
        verify(mockEntityManager).persist(aggregate);
        verify(mockEntityManager, never()).flush();
    }

    private class StubJpaAggregate {

        @Id
        private final String identifier;

        @SuppressWarnings("unused")
        @AggregateVersion
        @Version
        private long version;

        private StubJpaAggregate(String identifier) {
            this.identifier = identifier;
        }

        private StubJpaAggregate(String identifier, String... payloads) {
            this(identifier);
            for (String payload : payloads) {
                apply(payload);
            }
        }

        public void doSomething(String newValue) {
            apply(newValue);
        }

        public String getIdentifier() {
            return identifier;
        }
    }
}
