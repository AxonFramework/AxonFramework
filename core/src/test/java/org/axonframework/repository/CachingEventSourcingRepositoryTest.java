/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.repository;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.jcache.JCache;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.Event;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubAggregate;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.AggregateDeletedException;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.CachingEventSourcingRepository;
import org.axonframework.eventstore.EventStore;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.junit.*;

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
    private EventStore mockEventStore;
    private JCache cache;

    @Before
    public void setUp() {
        testSubject = new CachingEventSourcingRepository<StubAggregate>(new StubAggregateFactory());
        mockEventBus = mock(EventBus.class);
        testSubject.setEventBus(mockEventBus);

        mockEventStore = new InMemoryEventStore();
        testSubject.setEventStore(mockEventStore);

        cache = new JCache(CacheManager.getInstance().getCache("testCache"));
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
        List<Event> eventList = new ArrayList<Event>();
        while (events.hasNext()) {
            eventList.add(events.next());
        }
        assertEquals(2, eventList.size());
        verify(mockEventBus, times(2)).publish(isA(DomainEvent.class));
        cache.clear();

        reloadedAggregate1 = testSubject.load(aggregate1.getIdentifier(), null);

        assertNotSame(aggregate1, reloadedAggregate1);
        assertEquals(aggregate1.getVersion(),
                     reloadedAggregate1.getVersion());
    }

    @Test
    public void testLoadDeletedAggregate() {
        DefaultUnitOfWork.startAndGet();
        StubAggregate aggregate1 = new StubAggregate();
        testSubject.add(aggregate1);
        CurrentUnitOfWork.commit();

        AggregateIdentifier identifier = aggregate1.getIdentifier();

        DefaultUnitOfWork.startAndGet();
        aggregate1.delete();
        CurrentUnitOfWork.commit();

        DefaultUnitOfWork.startAndGet();
        try {
            testSubject.load(identifier);
            fail("Expected AggregateDeletedException");
        } catch (AggregateDeletedException e) {
            assertTrue(e.getMessage().contains(identifier.toString()));
        }
        CurrentUnitOfWork.commit();
    }

    private static class StubAggregateFactory implements AggregateFactory<StubAggregate> {

        @Override
        public StubAggregate createAggregate(AggregateIdentifier aggregateIdentifier, DomainEvent firstEvent) {
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

    private class InMemoryEventStore implements EventStore {

        private Map<AggregateIdentifier, List<DomainEvent>> store = new HashMap<AggregateIdentifier, List<DomainEvent>>();

        @Override
        public void appendEvents(String identifier, DomainEventStream events) {
            while (events.hasNext()) {
                DomainEvent next = events.next();
                if (!store.containsKey(next.getAggregateIdentifier())) {
                    store.put(next.getAggregateIdentifier(), new ArrayList<DomainEvent>());
                }
                List<DomainEvent> eventList = store.get(next.getAggregateIdentifier());
                eventList.add(next);
            }
        }

        @Override
        public DomainEventStream readEvents(String type, AggregateIdentifier identifier) {
            return new SimpleDomainEventStream(store.get(identifier));
        }
    }
}
