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
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.core.Ehcache;
import org.ehcache.core.EhcacheManager;
import org.ehcache.core.config.DefaultConfiguration;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
class CachingEventSourcingRepositoryTest {

    private CachingEventSourcingRepository<StubAggregate> testSubject;
    private EventStore mockEventStore;
    private Cache cache;
    private org.ehcache.core.Ehcache ehCache;
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        mockEventStore = spy(EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build());
        Map<String, CacheConfiguration<?, ?>> caches = new HashMap<>();
        DefaultConfiguration config = new DefaultConfiguration(caches, null);
        cacheManager = new EhcacheManager(config);
        cacheManager.init();
        ehCache = (Ehcache) cacheManager
                .createCache(
                        "testCache",
                        CacheConfigurationBuilder
                                .newCacheConfigurationBuilder(
                                        Object.class,
                                        Object.class,
                                        ResourcePoolsBuilder
                                                .newResourcePoolsBuilder()
                                                .heap(1, MemoryUnit.MB)
                                                .build())
                                .build());
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
        cacheManager.close();
    }

    @Test
    void aggregatesRetrievedFromCache() throws Exception {
        startAndGetUnitOfWork();

        LockAwareAggregate<StubAggregate, EventSourcedAggregate<StubAggregate>> aggregate1 =
                testSubject.newInstance(() -> new StubAggregate("aggregateId"));
        aggregate1.execute(StubAggregate::doSomething);
        assertEquals(0, aggregate1.getWrappedAggregate().lastSequence());
        CurrentUnitOfWork.commit();

        startAndGetUnitOfWork();
        LockAwareAggregate<StubAggregate, EventSourcedAggregate<StubAggregate>> reloadedAggregate1 =
                testSubject.load("aggregateId", null);
        assertEquals(0, reloadedAggregate1.getWrappedAggregate().lastSequence());
        aggregate1.execute(StubAggregate::doSomething);
        aggregate1.execute(StubAggregate::doSomething);
        assertEquals(2, aggregate1.getWrappedAggregate().lastSequence());
        CurrentUnitOfWork.commit();

        DefaultUnitOfWork.startAndGet(null);
        DomainEventStream events = mockEventStore.readEvents("aggregateId");
        List<EventMessage> eventList = new ArrayList<>();
        while (events.hasNext()) {
            eventList.add(events.next());
        }
        assertEquals(3, eventList.size());
        ehCache.clear();

        reloadedAggregate1 = testSubject.load(aggregate1.identifierAsString(), null);

        assertNotSame(aggregate1.getWrappedAggregate(), reloadedAggregate1.getWrappedAggregate());
        assertEquals(aggregate1.version(), reloadedAggregate1.version());
        assertEquals(2, reloadedAggregate1.getWrappedAggregate().lastSequence());
    }

    @Test
    void loadOrCreateNewAggregate() {
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
    void cacheClearedAfterRollbackOfLoadedAggregate() {

        startAndGetUnitOfWork().executeWithResult(() -> testSubject.newInstance(() -> new StubAggregate("id1")));

        UnitOfWork<?> uow = startAndGetUnitOfWork();
        uow.onCommit(c -> {
            throw new MockException();
        });
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
