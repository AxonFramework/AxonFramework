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

import com.fasterxml.jackson.databind.introspect.Annotated;
import net.sf.ehcache.CacheManager;
import org.axonframework.cache.Cache;
import org.axonframework.cache.EhCacheAdapter;
import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.commandhandling.model.AggregateLifecycle;
import org.axonframework.commandhandling.model.AggregateNotFoundException;
import org.axonframework.commandhandling.model.LockAwareAggregate;
import org.axonframework.commandhandling.model.inspection.AnnotatedAggregate;
import org.axonframework.commandhandling.model.inspection.EventSourcedAggregate;
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
import org.axonframework.testutils.MockException;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
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
        eventBus = mockEventStore;

        final CacheManager cacheManager = CacheManager.getInstance();
        ehCache = cacheManager.getCache("testCache");
        cache = spy(new EhCacheAdapter(ehCache));

        testSubject = new CachingEventSourcingRepository<>(new StubAggregateFactory(), mockEventStore, eventBus, cache);
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

//        // ensure the cached aggregate has been committed before being cached.
//        doThrow(new AssertionError("Aggregate should not have a null version when cached"))
//                .when(cache).put(eq("aggregateId"), argThat(new TypeSafeMatcher<Aggregate>() {
//            @Override
//            public boolean matchesSafely(Aggregate item) {
//                return item.version() == null;
//            }
//
//            @Override
//            public void describeTo(Description description) {
//                description.appendText("An aggregate with a non-null version");
//            }
//        }));

        LockAwareAggregate<StubAggregate, EventSourcedAggregate<StubAggregate>> aggregate1 = testSubject.newInstance(() -> new StubAggregate("aggregateId"));
        aggregate1.execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        startAndGetUnitOfWork();
        LockAwareAggregate<StubAggregate, EventSourcedAggregate<StubAggregate>> reloadedAggregate1 = testSubject.load("aggregateId", null);
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

        reloadedAggregate1 = testSubject.load(aggregate1.identifier(), null);

        assertNotSame(aggregate1.getWrappedAggregate(), reloadedAggregate1.getWrappedAggregate());
        assertEquals(aggregate1.version(),
                     reloadedAggregate1.version());
    }

    @Test
    public void testLoadDeletedAggregate() {
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
    @SuppressWarnings("unchecked")
    public void testCacheClearedAfterRollbackOfAddedAggregate() {
        UnitOfWork uow = startAndGetUnitOfWork();
        doThrow(new MockException()).when(mockEventStore).appendEvents(anyList());
        try {
            testSubject.newInstance(() -> new StubAggregate("id1")).execute(StubAggregate::doSomething);
            fail("Applied aggregate should have caused an exception");
        } catch (MockException e) {
            uow.rollback();
        }
        assertNull(cache.get("id1"));
    }

    private UnitOfWork startAndGetUnitOfWork() {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet(null);
        uow.resources().put(EventBus.KEY, eventBus);
        return uow;
    }

    private static class StubAggregateFactory extends AbstractAggregateFactory<StubAggregate> {

        public StubAggregateFactory() {
            super(StubAggregate.class);
        }

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
        }

        @Override
        public Registration subscribe(EventProcessor eventProcessor) {
            throw new UnsupportedOperationException();
        }
    }
}
