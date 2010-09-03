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
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.matchers.CapturingMatcher;

import java.util.Arrays;
import java.util.UUID;

import static org.mockito.Mockito.*;

/** @author Allard Buijze */
public class EventCountSnapshotterTriggerTest {

    private EventCountSnapshotterTrigger testSubject;
    private Snapshotter mockSnapshotter;
    private UUID aggregateIdentifier;
    private Cache mockCache;
    private CapturingMatcher<CacheListener> listener;

    @Before
    public void setUp() throws Exception {
        mockSnapshotter = mock(Snapshotter.class);
        testSubject = new EventCountSnapshotterTrigger();
        testSubject.setTrigger(3);
        testSubject.setSnapshotter(mockSnapshotter);
        mockCache = mock(Cache.class);
        listener = new CapturingMatcher<CacheListener>();
        doNothing().when(mockCache).addListener(argThat(listener));
    }

    @Test
    public void testSnapshotterTriggered() {
        aggregateIdentifier = UUID.randomUUID();

        readAllFrom(testSubject.decorateForRead("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 0),
                new StubDomainEvent(aggregateIdentifier, 1),
                new StubDomainEvent(aggregateIdentifier, 2)
        )));
        readAllFrom(testSubject.decorateForAppend("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 3)
        )));

        verify(mockSnapshotter).scheduleSnapshot("some", aggregateIdentifier);
    }

    @Test
    public void testSnapshotterNotTriggeredOnRead() {
        aggregateIdentifier = UUID.randomUUID();

        readAllFrom(testSubject.decorateForRead("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 0),
                new StubDomainEvent(aggregateIdentifier, 1),
                new StubDomainEvent(aggregateIdentifier, 2),
                new StubDomainEvent(aggregateIdentifier, 3)
        )));

        verify(mockSnapshotter, never()).scheduleSnapshot("some", aggregateIdentifier);
    }

    @Test
    public void testSnapshotterNotTriggeredOnSave() {
        aggregateIdentifier = UUID.randomUUID();

        readAllFrom(testSubject.decorateForRead("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 0),
                new StubDomainEvent(aggregateIdentifier, 1)
        )));
        readAllFrom(testSubject.decorateForAppend("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 2)
        )));

        verify(mockSnapshotter, never()).scheduleSnapshot("some", aggregateIdentifier);
    }

    @Test
    public void testCounterDoesNotResetWhenUsingCache() {
        testSubject.setAggregateCache(mockCache);
        aggregateIdentifier = UUID.randomUUID();
        readAllFrom(testSubject.decorateForRead("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 0),
                new StubDomainEvent(aggregateIdentifier, 1)
        )));
        readAllFrom(testSubject.decorateForAppend("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 2)
        )));
        readAllFrom(testSubject.decorateForAppend("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 3)
        )));

        verify(mockSnapshotter).scheduleSnapshot("some", aggregateIdentifier);
    }

    @Test
    public void testCounterResetWhenCacheEvictsEntry() {
        testSubject.setAggregateCaches(Arrays.asList(mockCache));
        aggregateIdentifier = UUID.randomUUID();

        readAllFrom(testSubject.decorateForRead("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 0),
                new StubDomainEvent(aggregateIdentifier, 1)
        )));
        readAllFrom(testSubject.decorateForAppend("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 2)
        )));

        listener.getLastValue().onEvict(aggregateIdentifier);

        readAllFrom(testSubject.decorateForAppend("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 3)
        )));

        verify(mockSnapshotter, never()).scheduleSnapshot("test", aggregateIdentifier);
    }

    @Test
    public void testCounterResetWhenCacheRemovesEntry() {
        testSubject.setAggregateCaches(Arrays.asList(mockCache));
        aggregateIdentifier = UUID.randomUUID();
        readAllFrom(testSubject.decorateForRead("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 0),
                new StubDomainEvent(aggregateIdentifier, 1)
        )));
        readAllFrom(testSubject.decorateForAppend("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 2)
        )));

        listener.getLastValue().onRemove(aggregateIdentifier);

        readAllFrom(testSubject.decorateForAppend("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 3)
        )));

        verify(mockSnapshotter, never()).scheduleSnapshot("test", aggregateIdentifier);
    }

    @Test
    public void testCounterResetWhenCacheCleared() {
        testSubject.setAggregateCaches(Arrays.asList(mockCache));
        aggregateIdentifier = UUID.randomUUID();

        readAllFrom(testSubject.decorateForRead("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 0),
                new StubDomainEvent(aggregateIdentifier, 1)
        )));
        readAllFrom(testSubject.decorateForAppend("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 2)
        )));

        listener.getLastValue().onClear();

        readAllFrom(testSubject.decorateForAppend("some", new SimpleDomainEventStream(
                new StubDomainEvent(aggregateIdentifier, 3)
        )));

        verify(mockSnapshotter, never()).scheduleSnapshot("test", aggregateIdentifier);
    }

    private StubDomainEvent newEvent() {
        return new StubDomainEvent(aggregateIdentifier);
    }

    private void readAllFrom(DomainEventStream events) {
        while (events.hasNext()) {
            events.next();
        }
    }
}
