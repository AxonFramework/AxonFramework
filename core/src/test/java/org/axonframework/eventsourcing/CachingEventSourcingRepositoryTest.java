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

import net.sf.ehcache.CacheManager;
import org.axonframework.cache.Cache;
import org.axonframework.cache.EhCacheAdapter;
import org.axonframework.common.Registration;
import org.axonframework.domain.StubAggregate;
import org.axonframework.eventhandling.AbstractEventBus;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventstore.EventStore;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.repository.AggregateNotFoundException;
import org.axonframework.testutils.MockException;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class CachingEventSourcingRepositoryTest {

    private CachingEventSourcingRepository<StubAggregate> testSubject;
    private EventBus eventBus;
    private InMemoryEventStore mockEventStore;
    private Cache cache;
    private net.sf.ehcache.Cache ehCache;

    @Before
    public void setUp() {
        mockEventStore = spy(new InMemoryEventStore());
        testSubject = new CachingEventSourcingRepository<>(new StubAggregateFactory(), mockEventStore);
        eventBus = mockEventStore;
        testSubject.setEventBus(eventBus);

        final CacheManager cacheManager = CacheManager.getInstance();
        ehCache = cacheManager.getCache("testCache");
        cache = spy(new EhCacheAdapter(ehCache));
        testSubject.setCache(cache);
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAggregatesRetrievedFromCache() {
        startAndGetUnitOfWork();
        final StubAggregate aggregate1 = new StubAggregate();
        aggregate1.doSomething();

        // ensure the cached aggregate has been committed before being cached.
        doThrow(new AssertionError("Aggregate should not have a null version when cached"))
                .when(cache).put(eq(aggregate1.getIdentifier()), argThat(new TypeSafeMatcher<StubAggregate>() {
            @Override
            public boolean matchesSafely(StubAggregate item) {
                return item.getVersion() == null;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("An aggregate with a non-null version");
            }
        }));

        testSubject.add(aggregate1);
        CurrentUnitOfWork.commit();

        startAndGetUnitOfWork();
        StubAggregate reloadedAggregate1 = testSubject.load(aggregate1.getIdentifier(), null);
        assertSame(aggregate1, reloadedAggregate1);
        aggregate1.doSomething();
        aggregate1.doSomething();
        CurrentUnitOfWork.commit();

        DefaultUnitOfWork.startAndGet(null);
        DomainEventStream events = mockEventStore.readEvents(aggregate1.getIdentifier());
        List<EventMessage> eventList = new ArrayList<>();
        while (events.hasNext()) {
            eventList.add(events.next());
        }
        assertEquals(3, eventList.size());
        ehCache.removeAll();

        reloadedAggregate1 = testSubject.load(aggregate1.getIdentifier(), null);

        assertNotSame(aggregate1, reloadedAggregate1);
        assertEquals(aggregate1.getVersion(),
                     reloadedAggregate1.getVersion());
    }

    //todo fix test
    @Ignore
    @Test
    public void testLoadAggregateWithExpectedVersion_ConcurrentModificationsDetected() {
        final ConflictResolver conflictResolver = mock(ConflictResolver.class);
        testSubject.setConflictResolver(conflictResolver);
        StubAggregate aggregate1 = new StubAggregate();

        startAndGetUnitOfWork();
        aggregate1.doSomething();
        aggregate1.doSomething();
        testSubject.add(aggregate1);
        CurrentUnitOfWork.commit();

        assertNotNull(((StubAggregate) cache.get(aggregate1.getIdentifier())).getVersion());

        startAndGetUnitOfWork();
        StubAggregate loadedAggregate = testSubject.load(aggregate1.getIdentifier(), 0L);
        loadedAggregate.doSomething();
        CurrentUnitOfWork.commit();

        assertEquals(3, mockEventStore.readEventsAsList(aggregate1.getIdentifier()).size());

        verify(conflictResolver).resolveConflicts(argThat(new TypeSafeMatcher<List<DomainEventMessage<?>>>() {
            @Override
            public boolean matchesSafely(List<DomainEventMessage<?>> item) {
                return item.size() == 1 && item.get(0).getSequenceNumber() == 2;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a list with 1 event, having sequence number 2");
            }
        }), argThat(new TypeSafeMatcher<List<DomainEventMessage<?>>>() {
            @Override
            public boolean matchesSafely(List<DomainEventMessage<?>> item) {
                return item.size() == 1 && item.get(0).getSequenceNumber() == 1;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a list with 1 event, having sequence number 1");
            }
        }));
    }

    @Test
    @Ignore
    public void testLoadAggregateFromCacheWithExpectedVersion_ConcurrentModificationsDetected() {
        mockEventStore = spy(new InMemoryEventStore());
        testSubject = new CachingEventSourcingRepository<>(new StubAggregateFactory(), mockEventStore);
        testSubject.setEventBus(eventBus);
        testSubject.setCache(cache);


        final ConflictResolver conflictResolver = mock(ConflictResolver.class);
        testSubject.setConflictResolver(conflictResolver);
        StubAggregate aggregate1 = new StubAggregate();

        startAndGetUnitOfWork();
        aggregate1.doSomething();
        aggregate1.doSomething();
        testSubject.add(aggregate1);
        CurrentUnitOfWork.commit();

        assertNotNull(((StubAggregate) cache.get(aggregate1.getIdentifier())).getVersion());

        startAndGetUnitOfWork();
        StubAggregate loadedAggregate = testSubject.load(aggregate1.getIdentifier(), 0L);
        loadedAggregate.doSomething();
        CurrentUnitOfWork.commit();

        assertEquals(3, mockEventStore.readEventsAsList(aggregate1.getIdentifier()).size());

        verify(mockEventStore, never()).readEvents(anyString(), anyObject());

        verify(conflictResolver).resolveConflicts(argThat(new TypeSafeMatcher<List<DomainEventMessage<?>>>() {
            @Override
            public boolean matchesSafely(List<DomainEventMessage<?>> item) {
                return item.size() == 1 && item.get(0).getSequenceNumber() == 2;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a list with 1 event, having sequence number 2");
            }
        }), argThat(new TypeSafeMatcher<List<DomainEventMessage<?>>>() {
            @Override
            public boolean matchesSafely(List<DomainEventMessage<?>> item) {
                return item.size() == 1 && item.get(0).getSequenceNumber() == 1;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a list with 1 event, having sequence number 1");
            }
        }));
    }

    @Test
    public void testLoadDeletedAggregate() {
        startAndGetUnitOfWork();
        StubAggregate aggregate1 = new StubAggregate();
        testSubject.add(aggregate1);
        CurrentUnitOfWork.commit();

        String identifier = aggregate1.getIdentifier();

        startAndGetUnitOfWork();
        aggregate1.delete();
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
    @SuppressWarnings("unchecked")
    public void testCacheClearedAfterRollbackOfAddedAggregate() {
        UnitOfWork<?> uow = startAndGetUnitOfWork();
        StubAggregate aggregate1 = new StubAggregate("id1");
        aggregate1.doSomething();
        testSubject.add(aggregate1);
        doThrow(new MockException()).when(mockEventStore).appendEvents(anyList());
        try {
            uow.commit();
        } catch (MockException e) {
            // whatever
        }
        String identifier = aggregate1.getIdentifier();
        assertNull(cache.get(identifier));
    }

    private UnitOfWork<?> startAndGetUnitOfWork() {
        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(null);
        uow.resources().put(EventBus.KEY, eventBus);
        return uow;
    }

    private static class StubAggregateFactory extends AbstractAggregateFactory<StubAggregate> {

        @Override
        public StubAggregate doCreateAggregate(String aggregateIdentifier, DomainEventMessage firstEvent) {
            return new StubAggregate(aggregateIdentifier);
        }

        @Override
        public Class<StubAggregate> getAggregateType() {
            return StubAggregate.class;
        }
    }

    private class InMemoryEventStore extends AbstractEventBus implements EventStore {

        protected Map<String, List<DomainEventMessage>> store = new HashMap<>();

        @Override
        public void appendEvents(List<DomainEventMessage<?>> events) {
            events.stream().forEach(event -> {
                DomainEventMessage next = (DomainEventMessage) event;
                if (!store.containsKey(next.getAggregateIdentifier())) {
                    store.put(next.getAggregateIdentifier(), new ArrayList<>());
                }
                List<DomainEventMessage> eventList = store.get(next.getAggregateIdentifier());
                eventList.add(next);
            });
        }

        @Override
        public DomainEventStream readEvents(String identifier, long firstSequenceNumber,
                                            long lastSequenceNumber) {
            if (!store.containsKey(identifier)) {
                throw new AggregateNotFoundException(identifier, "Aggregate not found");
            }
            final List<DomainEventMessage> events = store.get(identifier);
            List<DomainEventMessage> filteredEvents = new ArrayList<>();
            for (DomainEventMessage message : events) {
                if (message.getSequenceNumber() >= firstSequenceNumber
                        && message.getSequenceNumber() <= lastSequenceNumber) {
                    filteredEvents.add(message);
                }
            }
            return new SimpleDomainEventStream(filteredEvents);
        }

        public List<DomainEventMessage> readEventsAsList(String identifier) {
            return store.get(identifier);
        }

        @Override
        protected void commit(List<EventMessage<?>> events) {
            appendEvents(events.stream().map(event -> (DomainEventMessage<?>) event).collect(Collectors.toList()));
        }

        @Override
        public Registration subscribe(EventProcessor eventProcessor) {
            throw new UnsupportedOperationException();
        }
    }
}
