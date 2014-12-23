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

import org.axonframework.cache.Cache;
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

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class EventCountSnapshotterTriggerTest {

    private EventCountSnapshotterTrigger testSubject;
    private Snapshotter mockSnapshotter;
    private String aggregateIdentifier;
    private Cache mockCache;
    private CapturingMatcher<Cache.EntryListener> listenerConfiguration;
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
        listenerConfiguration = new CapturingMatcher<>();
        doNothing().when(mockCache).registerCacheEntryListener(argThat(listenerConfiguration));

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
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 0,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 1,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 2,
                                                      "Mock contents", MetaData.emptyInstance())
        )));
        readAllFrom(testSubject.decorateForAppend("some", aggregate, new SimpleDomainEventStream(
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 3,
                                                      "Mock contents", MetaData.emptyInstance())
        )));

        verify(mockSnapshotter, never()).scheduleSnapshot("some", aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter).scheduleSnapshot("some", aggregateIdentifier);
    }

    @Test
    public void testSnapshotterNotTriggeredOnRead() {
        readAllFrom(testSubject.decorateForRead("some", aggregateIdentifier, new SimpleDomainEventStream(
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 0,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 1,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 2,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 3,
                                                      "Mock contents", MetaData.emptyInstance())
        )));

        verify(mockSnapshotter, never()).scheduleSnapshot("some", aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter, never()).scheduleSnapshot("some", aggregateIdentifier);
    }

    @Test
    public void testSnapshotterNotTriggeredOnSave() {
        readAllFrom(testSubject.decorateForRead("some", aggregateIdentifier, new SimpleDomainEventStream(
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 0,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 1,
                                                      "Mock contents", MetaData.emptyInstance())
        )));
        readAllFrom(testSubject.decorateForAppend("some", aggregate, new SimpleDomainEventStream(
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 2,
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
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 0,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 1,
                                                      "Mock contents", MetaData.emptyInstance())
        )));
        readAllFrom(testSubject.decorateForAppend("some", aggregate, new SimpleDomainEventStream(
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 2,
                                                      "Mock contents", MetaData.emptyInstance())
        )));
        readAllFrom(testSubject.decorateForAppend("some", aggregate, new SimpleDomainEventStream(
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 3,
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
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 0,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 1,
                                                      "Mock contents", MetaData.emptyInstance())
        )));
        readAllFrom(testSubject.decorateForAppend("some", aggregate, new SimpleDomainEventStream(
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 2,
                                                      "Mock contents", MetaData.emptyInstance())
        )));

        final Cache.EntryListener listener = listenerConfiguration.getLastValue();
        listener.onEntryExpired(aggregateIdentifier);

        readAllFrom(testSubject.decorateForAppend("some", aggregate, new SimpleDomainEventStream(
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 3,
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
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 0,
                                                      "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 1,
                                                      "Mock contents", MetaData.emptyInstance())
        )));
        readAllFrom(testSubject.decorateForAppend("some", aggregate, new SimpleDomainEventStream(
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 2,
                                                      "Mock contents", MetaData.emptyInstance())
        )));

        final Cache.EntryListener listener = listenerConfiguration.getLastValue();
        listener.onEntryRemoved(aggregateIdentifier);

        readAllFrom(testSubject.decorateForAppend("some", aggregate, new SimpleDomainEventStream(
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 3,
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
}
