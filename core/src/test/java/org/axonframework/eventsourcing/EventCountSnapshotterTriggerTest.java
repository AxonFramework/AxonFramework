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

package org.axonframework.eventsourcing;

import net.sf.jsr107cache.Cache;
import net.sf.jsr107cache.CacheListener;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventstore.EventStore;
import org.junit.*;
import org.mockito.internal.matchers.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.Arrays;
import java.util.UUID;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class EventCountSnapshotterTriggerTest {

    private EventCountSnapshotterTrigger testSubject;
    private EventStore mockEventStore;
    private Snapshotter mockSnapshotter;
    private UUID aggregateIdentifier;
    private Cache mockCache;
    private CapturingMatcher<CacheListener> listener;

    @Before
    public void setUp() throws Exception {
        mockEventStore = mock(EventStore.class);
        mockSnapshotter = mock(Snapshotter.class);
        testSubject = new EventCountSnapshotterTrigger();
        testSubject.setEventStore(mockEventStore);
        testSubject.setDefaultTrigger(3);
        testSubject.setSnapshotter(mockSnapshotter);
        testSubject.registerTrigger("test", 5);
        doAnswer(new EventStreamReader())
                .when(mockEventStore).appendEvents(isA(String.class), isA(DomainEventStream.class));
        mockCache = mock(Cache.class);
        listener = new CapturingMatcher<CacheListener>();
        doNothing().when(mockCache).addListener(argThat(listener));
    }

    @Test
    public void testSnapshotterTriggered_DefaultSetting() {
        aggregateIdentifier = UUID.randomUUID();
        when(mockEventStore.readEvents("some", aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(newEvent(), newEvent()));

        readAllFrom(testSubject.readEvents("some", aggregateIdentifier));
        testSubject.appendEvents("some", new SimpleDomainEventStream(newEvent(), newEvent()));

        verify(mockSnapshotter).scheduleSnapshot("some", aggregateIdentifier);
    }

    @Test
    public void testSnapshotterNotTriggered_DefaultSetting() {
        aggregateIdentifier = UUID.randomUUID();
        when(mockEventStore.readEvents("some", aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(newEvent(), newEvent()));

        readAllFrom(testSubject.readEvents("some", aggregateIdentifier));
        testSubject.appendEvents("some", new SimpleDomainEventStream(newEvent()));

        verify(mockSnapshotter, never()).scheduleSnapshot("some", aggregateIdentifier);
    }

    @Test
    public void testSnapshotterNotTriggered_DefaultSetting_CounterIsCleared() {
        aggregateIdentifier = UUID.randomUUID();
        when(mockEventStore.readEvents("some", aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(newEvent(), newEvent()));

        readAllFrom(testSubject.readEvents("some", aggregateIdentifier));
        testSubject.appendEvents("some", new SimpleDomainEventStream(newEvent()));
        testSubject.appendEvents("some", new SimpleDomainEventStream(newEvent()));

        verify(mockSnapshotter, never()).scheduleSnapshot("some", aggregateIdentifier);
    }

    @Test
    public void testSnapshotterTriggered_SpecialSetting() {
        aggregateIdentifier = UUID.randomUUID();
        when(mockEventStore.readEvents("test", aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(newEvent(), newEvent(), newEvent(), newEvent()));

        readAllFrom(testSubject.readEvents("test", aggregateIdentifier));
        testSubject.appendEvents("test", new SimpleDomainEventStream(newEvent(), newEvent()));

        verify(mockSnapshotter).scheduleSnapshot("test", aggregateIdentifier);
    }

    @Test
    public void testSnapshotterNotTriggered_SpecialSetting() {
        aggregateIdentifier = UUID.randomUUID();
        when(mockEventStore.readEvents("test", aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(newEvent(), newEvent(), newEvent(), newEvent()));

        readAllFrom(testSubject.readEvents("test", aggregateIdentifier));
        testSubject.appendEvents("test", new SimpleDomainEventStream(newEvent()));

        verify(mockSnapshotter, never()).scheduleSnapshot("test", aggregateIdentifier);
    }

    @Test
    public void testSnapshotterNotTriggered_SpecialSetting_CounterIsCleared() {
        aggregateIdentifier = UUID.randomUUID();
        when(mockEventStore.readEvents("test", aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(newEvent(), newEvent(), newEvent(), newEvent()));

        readAllFrom(testSubject.readEvents("test", aggregateIdentifier));
        testSubject.appendEvents("test", new SimpleDomainEventStream(newEvent()));
        testSubject.appendEvents("test", new SimpleDomainEventStream(newEvent()));

        verify(mockSnapshotter, never()).scheduleSnapshot("test", aggregateIdentifier);
    }

    @Test
    public void testCounterDoesNotResetWhenUsingCache() {
        testSubject.setAggregateCache(mockCache);
        aggregateIdentifier = UUID.randomUUID();
        when(mockEventStore.readEvents("test", aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(newEvent(), newEvent(), newEvent(), newEvent()));

        readAllFrom(testSubject.readEvents("test", aggregateIdentifier));
        testSubject.appendEvents("test", new SimpleDomainEventStream(newEvent()));
        testSubject.appendEvents("test", new SimpleDomainEventStream(newEvent()));

        verify(mockSnapshotter).scheduleSnapshot("test", aggregateIdentifier);
    }

    @Test
    public void testCounterResetWhenCacheEvictsEntry() {
        testSubject.setAggregateCaches(Arrays.asList(mockCache));
        aggregateIdentifier = UUID.randomUUID();
        when(mockEventStore.readEvents("test", aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(newEvent(), newEvent(), newEvent(), newEvent()));

        readAllFrom(testSubject.readEvents("test", aggregateIdentifier));
        testSubject.appendEvents("test", new SimpleDomainEventStream(newEvent()));
        listener.getLastValue().onEvict(aggregateIdentifier);
        testSubject.appendEvents("test", new SimpleDomainEventStream(newEvent()));

        verify(mockSnapshotter, never()).scheduleSnapshot("test", aggregateIdentifier);
    }

    @Test
    public void testCounterResetWhenCacheRemovesEntry() {
        testSubject.setAggregateCaches(Arrays.asList(mockCache));
        aggregateIdentifier = UUID.randomUUID();
        when(mockEventStore.readEvents("test", aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(newEvent(), newEvent(), newEvent(), newEvent()));

        readAllFrom(testSubject.readEvents("test", aggregateIdentifier));
        testSubject.appendEvents("test", new SimpleDomainEventStream(newEvent()));
        listener.getLastValue().onRemove(aggregateIdentifier);
        testSubject.appendEvents("test", new SimpleDomainEventStream(newEvent()));

        verify(mockSnapshotter, never()).scheduleSnapshot("test", aggregateIdentifier);
    }

    @Test
    public void testCounterResetWhenCacheCleared() {
        testSubject.setAggregateCaches(Arrays.asList(mockCache));
        aggregateIdentifier = UUID.randomUUID();
        when(mockEventStore.readEvents("test", aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(newEvent(), newEvent(), newEvent(), newEvent()));

        readAllFrom(testSubject.readEvents("test", aggregateIdentifier));
        testSubject.appendEvents("test", new SimpleDomainEventStream(newEvent()));
        listener.getLastValue().onClear();
        testSubject.appendEvents("test", new SimpleDomainEventStream(newEvent()));

        verify(mockSnapshotter, never()).scheduleSnapshot("test", aggregateIdentifier);
    }

    @Test
    public void testStoringAggregateWithoutChanges() {
        SimpleDomainEventStream emptyEventStream = new SimpleDomainEventStream();
        testSubject.appendEvents("test", emptyEventStream);

        verify(mockEventStore).appendEvents("test", emptyEventStream);
    }

    private StubDomainEvent newEvent() {
        return new StubDomainEvent(aggregateIdentifier);
    }

    private void readAllFrom(DomainEventStream events) {
        while (events.hasNext()) {
            events.next();
        }
    }

    private class EventStreamReader implements Answer<Object> {

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            DomainEventStream events = (DomainEventStream) invocation.getArguments()[1];
            readAllFrom(events);
            return null;
        }
    }
}
