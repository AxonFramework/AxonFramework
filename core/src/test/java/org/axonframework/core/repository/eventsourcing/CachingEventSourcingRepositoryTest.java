/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core.repository.eventsourcing;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.jcache.JCache;
import org.axonframework.core.DomainEvent;
import org.axonframework.core.DomainEventStream;
import org.axonframework.core.Event;
import org.axonframework.core.SimpleDomainEventStream;
import org.axonframework.core.StubAggregate;
import org.axonframework.core.eventhandler.EventBus;
import org.junit.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        testSubject = new StubCachingEventSourcingRepository();
        mockEventBus = mock(EventBus.class);
        testSubject.setEventBus(mockEventBus);

        mockEventStore = new InMemoryEventStore();
        testSubject.setEventStore(mockEventStore);

        cache = new JCache(CacheManager.getInstance().getCache("testCache"));
        testSubject.setCache(cache);
    }

    @Test
    public void testAggregatesRetrievedFromCache() {
        StubAggregate aggregate1 = new StubAggregate();
        testSubject.save(aggregate1);

        StubAggregate reloadedAggregate1 = testSubject.load(aggregate1.getIdentifier());
        assertSame(aggregate1, reloadedAggregate1);
        aggregate1.doSomething();
        aggregate1.doSomething();
        testSubject.save(aggregate1);

        DomainEventStream events = mockEventStore.readEvents("mock", aggregate1.getIdentifier());
        List<Event> eventList = new ArrayList<Event>();
        while (events.hasNext()) {
            eventList.add(events.next());
        }
        assertEquals(2, eventList.size());
        verify(mockEventBus, times(2)).publish(isA(DomainEvent.class));
        cache.clear();

        reloadedAggregate1 = testSubject.load(aggregate1.getIdentifier());

        assertNotSame(aggregate1, reloadedAggregate1);
        assertEquals(aggregate1.getLastCommittedEventSequenceNumber(),
                     reloadedAggregate1.getLastCommittedEventSequenceNumber());
    }

    private static class StubCachingEventSourcingRepository extends CachingEventSourcingRepository<StubAggregate> {

        @Override
        protected StubAggregate instantiateAggregate(UUID aggregateIdentifier) {
            return new StubAggregate(aggregateIdentifier);
        }

        @Override
        protected String getTypeIdentifier() {
            return "mock";
        }
    }

    private class InMemoryEventStore implements EventStore {

        private Map<UUID, List<DomainEvent>> store = new HashMap<UUID, List<DomainEvent>>();

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
        public DomainEventStream readEvents(String type, UUID identifier) {
            return new SimpleDomainEventStream(store.get(identifier));
        }
    }
}
