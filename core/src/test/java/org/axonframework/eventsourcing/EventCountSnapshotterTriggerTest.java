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

import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubAggregate;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;
import org.mockito.internal.matchers.*;

import java.util.Arrays;
import javax.cache.Cache;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryRemovedListener;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class EventCountSnapshotterTriggerTest {

    private EventCountSnapshotterTrigger testSubject;
    private Snapshotter mockSnapshotter;
    private Object aggregateIdentifier;
    private Cache<Object, Object> mockCache;
    private CapturingMatcher<CacheEntryListener<Object, Object>> listenerConfiguration;
    private EventSourcedAggregateRoot aggregate;

    private UnitOfWork unitOfWork;

    @Before
    public void setUp() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
        mockSnapshotter = mock(Snapshotter.class);
        testSubject = new EventCountSnapshotterTrigger();
        testSubject.setTrigger(3);
        testSubject.setSnapshotter(mockSnapshotter);
        aggregateIdentifier = "aggregateIdentifier";
        aggregate = new StubAggregate(aggregateIdentifier);
        //noinspection unchecked
        mockCache = mock(Cache.class);
        listenerConfiguration = new CapturingMatcher<CacheEntryListener<Object, Object>>();
        doReturn(false).when(mockCache).registerCacheEntryListener(argThat(listenerConfiguration));

        unitOfWork = DefaultUnitOfWork.startAndGet();
    }

    @After
    public void tearDown() {
        try {
            if (unitOfWork.isStarted()) {
                unitOfWork.rollback();
            }
        } finally {
            while (CurrentUnitOfWork.isStarted()) {
                CurrentUnitOfWork.get().rollback();
                System.out.println(
                        "Warning!! EventCountSnapshotterTriggerTest seems to no correctly close all UnitOfWork");
            }
        }
    }

    @Test
    public void testSnapshotterTriggered() {
        readAllFrom(testSubject.decorateForRead("some", aggregateIdentifier, new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 0,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 1,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 2,
                                                      "Mock contents", MetaData.emptyInstance())
        )));
        readAllFrom(testSubject.decorateForAppend("some", aggregate, new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 3,
                                                      "Mock contents", MetaData.emptyInstance())
        )));

        verify(mockSnapshotter, never()).scheduleSnapshot("some", aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter).scheduleSnapshot("some", aggregateIdentifier);
    }

    @Test
    public void testSnapshotterNotTriggeredOnRead() {
        readAllFrom(testSubject.decorateForRead("some", aggregateIdentifier, new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 0,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 1,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 2,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 3,
                                                      "Mock contents", MetaData.emptyInstance())
        )));

        verify(mockSnapshotter, never()).scheduleSnapshot("some", aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter, never()).scheduleSnapshot("some", aggregateIdentifier);
    }

    @Test
    public void testSnapshotterNotTriggeredOnSave() {
        readAllFrom(testSubject.decorateForRead("some", aggregateIdentifier, new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 0,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 1,
                                                      "Mock contents", MetaData.emptyInstance())
        )));
        readAllFrom(testSubject.decorateForAppend("some", aggregate, new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 2,
                                                      "Mock contents", MetaData.emptyInstance())
        )));

        verify(mockSnapshotter, never()).scheduleSnapshot("some", aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter, never()).scheduleSnapshot("some", aggregateIdentifier);
    }

    @Test
    public void testCounterDoesNotResetWhenUsingCache() {
        testSubject.setAggregateCache(mockCache);
        readAllFrom(testSubject.decorateForRead("some", aggregateIdentifier, new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 0,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 1,
                                                      "Mock contents", MetaData.emptyInstance())
        )));
        readAllFrom(testSubject.decorateForAppend("some", aggregate, new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 2,
                                                      "Mock contents", MetaData.emptyInstance())
        )));
        readAllFrom(testSubject.decorateForAppend("some", aggregate, new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 3,
                                                      "Mock contents", MetaData.emptyInstance())
        )));

        verify(mockSnapshotter, never()).scheduleSnapshot("some", aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter).scheduleSnapshot("some", aggregateIdentifier);
    }

    @Test
    public void testCounterResetWhenCacheEvictsEntry() {
        testSubject.setAggregateCaches(Arrays.<Cache>asList(mockCache));
        readAllFrom(testSubject.decorateForRead("some", aggregateIdentifier, new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 0,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 1,
                                                      "Mock contents", MetaData.emptyInstance())
        )));
        readAllFrom(testSubject.decorateForAppend("some", aggregate, new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 2,
                                                      "Mock contents", MetaData.emptyInstance())
        )));

        final CacheEntryListener<? super Object, ? super Object> listener = listenerConfiguration
                .getLastValue();
        assertTrue(listener instanceof CacheEntryExpiredListener<?, ?>);
        ((CacheEntryExpiredListener) listener).entryExpired(new StubCacheEntryEvent());

        readAllFrom(testSubject.decorateForAppend("some", aggregate, new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 3,
                                                      "Mock contents", MetaData.emptyInstance())
        )));

        verify(mockSnapshotter, never()).scheduleSnapshot("some", aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter, never()).scheduleSnapshot("test", aggregateIdentifier);
    }

    @Test
    public void testCounterResetWhenCacheRemovesEntry() {
        testSubject.setAggregateCaches(Arrays.<Cache>asList(mockCache));
        readAllFrom(testSubject.decorateForRead("some", aggregateIdentifier, new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 0,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 1,
                                                      "Mock contents", MetaData.emptyInstance())
        )));
        readAllFrom(testSubject.decorateForAppend("some", aggregate, new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 2,
                                                      "Mock contents", MetaData.emptyInstance())
        )));

        final CacheEntryListener<? super Object, ? super Object> listener = listenerConfiguration
                .getLastValue();
        assertTrue(listener instanceof CacheEntryRemovedListener<?, ?>);
        ((CacheEntryRemovedListener) listener).entryRemoved(new StubCacheEntryEvent());

        readAllFrom(testSubject.decorateForAppend("some", aggregate, new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 3,
                                                      "Mock contents", MetaData.emptyInstance())
        )));

        verify(mockSnapshotter, never()).scheduleSnapshot("some", aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter, never()).scheduleSnapshot("test", aggregateIdentifier);
    }

    @Test
    public void testStoringAggregateWithoutChanges() {
        SimpleDomainEventStream emptyEventStream = new SimpleDomainEventStream();
        testSubject.decorateForAppend("test", aggregate, emptyEventStream);

        verify(mockSnapshotter, never()).scheduleSnapshot("some", aggregateIdentifier);
        CurrentUnitOfWork.commit();
    }

    private void readAllFrom(DomainEventStream events) {
        while (events.hasNext()) {
            events.next();
        }
    }

    private class StubCacheEntryEvent extends CacheEntryEvent {

        public StubCacheEntryEvent() {
            super(EventCountSnapshotterTriggerTest.this.mockCache);
        }

        @Override
        public Object getKey() {
            return aggregateIdentifier;
        }

        @Override
        public Object getValue() {
            return null;
        }
    }
}
