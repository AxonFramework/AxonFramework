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
import net.sf.ehcache.jcache.JCache;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubAggregate;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.PartialStreamSupport;
import org.axonframework.repository.AggregateNotFoundException;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.hamcrest.Description;
import org.junit.*;
import org.junit.internal.matchers.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class CachingEventSourcingRepositoryTest {

    private CachingEventSourcingRepository<StubAggregate> testSubject;
    private EventBus mockEventBus;
    private InMemoryEventStore mockEventStore;
    private JCache cache;

    @Before
    public void setUp() {
        mockEventStore = spy(new InMemoryEventStore());
        testSubject = new CachingEventSourcingRepository<StubAggregate>(new StubAggregateFactory(), mockEventStore);
        mockEventBus = mock(EventBus.class);
        testSubject.setEventBus(mockEventBus);

        cache = spy(new JCache(CacheManager.getInstance().getCache("testCache")));
        testSubject.setCache(cache);
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testAggregatesRetrievedFromCache() {
        DefaultUnitOfWork.startAndGet();
        StubAggregate aggregate1 = new StubAggregate();
        aggregate1.doSomething();

        // ensure the cached aggregate has been committed before being cached.
        when(cache.put(eq(aggregate1.getIdentifier()), argThat(new TypeSafeMatcher<StubAggregate>() {
            @Override
            public boolean matchesSafely(StubAggregate item) {
                return item.getVersion() == null;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("An aggregate with a non-null version");
            }
        }))).thenThrow(new AssertionError("Aggregate should not have a null version when cached"));

        testSubject.add(aggregate1);
        CurrentUnitOfWork.commit();

        DefaultUnitOfWork.startAndGet();
        StubAggregate reloadedAggregate1 = testSubject.load(aggregate1.getIdentifier(), null);
        assertSame(aggregate1, reloadedAggregate1);
        aggregate1.doSomething();
        aggregate1.doSomething();
        CurrentUnitOfWork.commit();

        DefaultUnitOfWork.startAndGet();
        DomainEventStream events = mockEventStore.readEvents("mock", aggregate1.getIdentifier());
        List<EventMessage> eventList = new ArrayList<EventMessage>();
        while (events.hasNext()) {
            eventList.add(events.next());
        }
        assertEquals(3, eventList.size());
        verify(mockEventBus).publish(isA(EventMessage.class));
        verify(mockEventBus).publish(isA(EventMessage.class), isA(EventMessage.class));
        verifyNoMoreInteractions(mockEventBus);
        cache.clear();

        reloadedAggregate1 = testSubject.load(aggregate1.getIdentifier(), null);

        assertNotSame(aggregate1, reloadedAggregate1);
        assertEquals(aggregate1.getVersion(),
                     reloadedAggregate1.getVersion());
    }

    @Test
    public void testLoadAggregateWithExpectedVersion_ConcurrentModificationsDetected() {
        final ConflictResolver conflictResolver = mock(ConflictResolver.class);
        testSubject.setConflictResolver(conflictResolver);
        StubAggregate aggregate1 = new StubAggregate();

        DefaultUnitOfWork.startAndGet();
        aggregate1.doSomething();
        aggregate1.doSomething();
        testSubject.add(aggregate1);
        CurrentUnitOfWork.commit();

        assertNotNull(((StubAggregate) cache.get(aggregate1.getIdentifier())).getVersion());

        DefaultUnitOfWork.startAndGet();
        StubAggregate loadedAggregate = testSubject.load(aggregate1.getIdentifier(), 0L);
        loadedAggregate.doSomething();
        CurrentUnitOfWork.commit();

        assertEquals(3, mockEventStore.readEventsAsList(aggregate1.getIdentifier()).size());

        verify(conflictResolver).resolveConflicts(argThat(new TypeSafeMatcher<List<DomainEventMessage>>() {
            @Override
            public boolean matchesSafely(List<DomainEventMessage> item) {
                return item.size() == 1 && item.get(0).getSequenceNumber() == 2;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a list with 1 event, having sequence number 2");
            }
        }), argThat(new TypeSafeMatcher<List<DomainEventMessage>>() {
            @Override
            public boolean matchesSafely(List<DomainEventMessage> item) {
                return item.size() == 1 && item.get(0).getSequenceNumber() == 1;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a list with 1 event, having sequence number 1");
            }
        }));
    }

    @Test
    public void testLoadAggregateFromCacheWithExpectedVersion_ConcurrentModificationsDetected() {
        // configure a repository that uses a PartialStreamSupport Event store
        mockEventStore = spy(new PartialReadingInMemoryEventStore());
        testSubject = new CachingEventSourcingRepository<StubAggregate>(new StubAggregateFactory(), mockEventStore);
        testSubject.setEventBus(mockEventBus);
        testSubject.setCache(cache);


        final ConflictResolver conflictResolver = mock(ConflictResolver.class);
        testSubject.setConflictResolver(conflictResolver);
        StubAggregate aggregate1 = new StubAggregate();

        DefaultUnitOfWork.startAndGet();
        aggregate1.doSomething();
        aggregate1.doSomething();
        testSubject.add(aggregate1);
        CurrentUnitOfWork.commit();

        assertNotNull(((StubAggregate) cache.get(aggregate1.getIdentifier())).getVersion());

        DefaultUnitOfWork.startAndGet();
        StubAggregate loadedAggregate = testSubject.load(aggregate1.getIdentifier(), 0L);
        loadedAggregate.doSomething();
        CurrentUnitOfWork.commit();

        assertEquals(3, mockEventStore.readEventsAsList(aggregate1.getIdentifier()).size());

        verify(mockEventStore, never()).readEvents(anyString(), anyObject());

        verify(conflictResolver).resolveConflicts(argThat(new TypeSafeMatcher<List<DomainEventMessage>>() {
            @Override
            public boolean matchesSafely(List<DomainEventMessage> item) {
                return item.size() == 1 && item.get(0).getSequenceNumber() == 2;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a list with 1 event, having sequence number 2");
            }
        }), argThat(new TypeSafeMatcher<List<DomainEventMessage>>() {
            @Override
            public boolean matchesSafely(List<DomainEventMessage> item) {
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
        DefaultUnitOfWork.startAndGet();
        StubAggregate aggregate1 = new StubAggregate();
        testSubject.add(aggregate1);
        CurrentUnitOfWork.commit();

        Object identifier = aggregate1.getIdentifier();

        DefaultUnitOfWork.startAndGet();
        aggregate1.delete();
        CurrentUnitOfWork.commit();

        DefaultUnitOfWork.startAndGet();
        try {
            testSubject.load(identifier);
            fail("Expected AggregateDeletedException");
        } catch (AggregateDeletedException e) {
            assertTrue(e.getMessage().contains(identifier.toString()));
        } finally {
            CurrentUnitOfWork.commit();
        }
    }

    @Test
    public void testCacheClearedAfterRollbackOfAddedAggregate() {
        DefaultUnitOfWork.startAndGet();
        StubAggregate aggregate1 = new StubAggregate("id1");
        aggregate1.doSomething();
        testSubject.add(aggregate1);
        doThrow(new RuntimeException("Mock - simulate failure")).when(mockEventBus).publish(isA(EventMessage.class));
        try {
            CurrentUnitOfWork.get().commit();
        } catch (RuntimeException e) {
            // whatever
        }
        Object identifier = aggregate1.getIdentifier();
        assertEquals(null, cache.get(identifier));
    }

    private static class StubAggregateFactory extends AbstractAggregateFactory<StubAggregate> {

        @Override
        public StubAggregate doCreateAggregate(Object aggregateIdentifier, DomainEventMessage firstEvent) {
            return new StubAggregate(aggregateIdentifier);
        }

        @Override
        public String getTypeIdentifier() {
            return "mock";
        }

        @Override
        public Class<StubAggregate> getAggregateType() {
            return StubAggregate.class;
        }
    }

    private class PartialReadingInMemoryEventStore extends InMemoryEventStore implements PartialStreamSupport {

        @Override
        public DomainEventStream readEvents(String type, Object identifier, long firstSequenceNumber) {
            return readEvents(type, identifier, firstSequenceNumber, Long.MAX_VALUE);
        }

        @Override
        public DomainEventStream readEvents(String type, Object identifier, long firstSequenceNumber,
                                            long lastSequenceNumber) {
            if (!store.containsKey(identifier)) {
                throw new AggregateNotFoundException(identifier, "Aggregate not found");
            }
            final List<DomainEventMessage> events = store.get(identifier);
            List<DomainEventMessage> filteredEvents = new ArrayList<DomainEventMessage>();
            for (DomainEventMessage message : events) {
                if (message.getSequenceNumber() >= firstSequenceNumber
                        && message.getSequenceNumber() <= lastSequenceNumber) {
                    filteredEvents.add(message);
                }
            }
            return new SimpleDomainEventStream(filteredEvents);
        }
    }

    private class InMemoryEventStore implements EventStore {

        protected Map<Object, List<DomainEventMessage>> store = new HashMap<Object, List<DomainEventMessage>>();

        @Override
        public void appendEvents(String identifier, DomainEventStream events) {
            while (events.hasNext()) {
                DomainEventMessage next = events.next();
                if (!store.containsKey(next.getAggregateIdentifier())) {
                    store.put(next.getAggregateIdentifier(), new ArrayList<DomainEventMessage>());
                }
                List<DomainEventMessage> eventList = store.get(next.getAggregateIdentifier());
                eventList.add(next);
            }
        }

        @Override
        public DomainEventStream readEvents(String type, Object identifier) {
            if (!store.containsKey(identifier)) {
                throw new AggregateNotFoundException(identifier, "Aggregate not found");
            }
            return new SimpleDomainEventStream(store.get(identifier));
        }

        public List<DomainEventMessage> readEventsAsList(Object identifier) {
            return store.get(identifier);
        }
    }
}
