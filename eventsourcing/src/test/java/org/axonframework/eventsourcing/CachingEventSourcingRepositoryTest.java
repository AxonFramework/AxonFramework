/*
 * Copyright (c) 2010-2018. Axon Framework
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

import net.sf.ehcache.CacheManager;
import org.axonframework.common.caching.Cache;
import org.axonframework.common.caching.EhCacheAdapter;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.eventsourcing.utils.MockException;
import org.axonframework.eventsourcing.utils.StubAggregate;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.modelling.command.LockAwareAggregate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * @author Allard Buijze
 */
class CachingEventSourcingRepositoryTest {

    private CachingEventSourcingRepository<StubAggregate> testSubject;
    private EventStore mockEventStore;
    private Cache cache;
    private net.sf.ehcache.Cache ehCache;

    @BeforeEach
    void setUp() {
        mockEventStore = spy(EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build());

        final CacheManager cacheManager = CacheManager.getInstance();
        ehCache = cacheManager.getCache("testCache");
        cache = spy(new EhCacheAdapter(ehCache));

        testSubject = CachingEventSourcingRepository.builder(StubAggregate.class)
                .aggregateFactory(new StubAggregateFactory())
                .eventStore(mockEventStore)
                .cache(cache)
                .build();
    }

    @AfterEach
    void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    void aggregatesRetrievedFromCache() throws Exception {
        startAndGetUnitOfWork();

        LockAwareAggregate<StubAggregate, EventSourcedAggregate<StubAggregate>> aggregate1 =
                testSubject.newInstance(() -> new StubAggregate("aggregateId"));
        aggregate1.execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        startAndGetUnitOfWork();
        LockAwareAggregate<StubAggregate, EventSourcedAggregate<StubAggregate>> reloadedAggregate1 =
                testSubject.load("aggregateId", null);
        assertSame(aggregate1.getWrappedAggregate(), reloadedAggregate1.getWrappedAggregate());
        aggregate1.execute(StubAggregate::doSomething);
        aggregate1.execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        DefaultUnitOfWork.startAndGet(null);
        DomainEventStream events = mockEventStore.readEvents("aggregateId");
        List<EventMessage> eventList = new ArrayList<>();
        while (events.hasNext()) {
            eventList.add(events.next());
        }
        assertEquals(3, eventList.size());
        ehCache.removeAll();

        reloadedAggregate1 = testSubject.load(aggregate1.identifierAsString(), null);

        assertNotSame(aggregate1.getWrappedAggregate(), reloadedAggregate1.getWrappedAggregate());
        assertEquals(aggregate1.version(),
                     reloadedAggregate1.version());
    }

    @Test
    void loadOrCreateNewAggregate() throws Exception {
        startAndGetUnitOfWork();
        Aggregate<StubAggregate> aggregate = testSubject.loadOrCreate("id1", StubAggregate::new);
        aggregate.execute(s -> s.setIdentifier("id1"));

        CurrentUnitOfWork.commit();

        assertNotNull(cache.get("id1"));
        verify(cache, never()).put(isNull(), any());
    }

    @Test
    void loadDeletedAggregate() throws Exception {
        String identifier = "aggregateId";

        startAndGetUnitOfWork();
        Aggregate<StubAggregate> aggregate1 = testSubject.newInstance(() -> new StubAggregate(identifier));
        CurrentUnitOfWork.commit();


        startAndGetUnitOfWork();
        testSubject.load(identifier).execute((r) -> AggregateLifecycle.markDeleted());
        CurrentUnitOfWork.commit();

        startAndGetUnitOfWork();
        try {
            testSubject.load(identifier);
            fail("Expected AggregateDeletedException");
        } catch (AggregateDeletedException e) {
            assertTrue(e.getMessage().contains(identifier));
        } finally {
            CurrentUnitOfWork.commit();
        }
    }

    @Test
    void cacheClearedAfterRollbackOfAddedAggregate() throws Exception {
        UnitOfWork<?> uow = startAndGetUnitOfWork();
        uow.onCommit(c -> { throw new MockException();});
        try {
            testSubject.newInstance(() -> new StubAggregate("id1")).execute(StubAggregate::doSomething);
            uow.commit();
        } catch (MockException e) {
            // great, that's what we expect
        }
        assertNull(cache.get("id1"));
    }

    @Test
    void cacheClearedAfterRollbackOfLoadedAggregate() throws Exception {

        startAndGetUnitOfWork().executeWithResult(() -> testSubject.newInstance(() -> new StubAggregate("id1")));

        UnitOfWork<?> uow = startAndGetUnitOfWork();
        uow.onCommit(c -> { throw new MockException();});
        try {
            testSubject.load("id1").execute(StubAggregate::doSomething);
            uow.commit();
        } catch (MockException e) {
            // great, that's what we expect
        }
        assertNull(cache.get("id1"));
    }

    @Test
    void cacheClearedAfterRollbackOfLoadedAggregateUsingLoadOrCreate() throws Exception {

        startAndGetUnitOfWork().executeWithResult(() -> testSubject.newInstance(() -> new StubAggregate("id1")));

        UnitOfWork<?> uow = startAndGetUnitOfWork();
        uow.onCommit(c -> { throw new MockException();});
        try {
            testSubject.loadOrCreate("id1", () -> new StubAggregate("id1")).execute(StubAggregate::doSomething);
            uow.commit();
        } catch (MockException e) {
            // great, that's what we expect
        }
        assertNull(cache.get("id1"));
    }

    @Test
    void cacheClearedAfterRollbackOfCreatedAggregateUsingLoadOrCreate() throws Exception {

        UnitOfWork<?> uow = startAndGetUnitOfWork();
        uow.onCommit(c -> { throw new MockException();});
        try {
            testSubject.loadOrCreate("id1", () -> new StubAggregate("id1")).execute(StubAggregate::doSomething);
            uow.commit();
        } catch (MockException e) {
            // great, that's what we expect
        }
        assertNull(cache.get("id1"));
    }

    private UnitOfWork<?> startAndGetUnitOfWork() {
        return DefaultUnitOfWork.startAndGet(null);
    }

    private static class StubAggregateFactory extends AbstractAggregateFactory<StubAggregate> {

        public StubAggregateFactory() {
            super(StubAggregate.class);
        }

        @Override
        public StubAggregate doCreateAggregate(String aggregateIdentifier, DomainEventMessage firstEvent) {
            return new StubAggregate();
        }

        @Override
        public Class<StubAggregate> getAggregateType() {
            return StubAggregate.class;
        }
    }
}
