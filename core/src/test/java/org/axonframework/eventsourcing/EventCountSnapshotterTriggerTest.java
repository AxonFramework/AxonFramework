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

import org.axonframework.commandhandling.StubAggregate;
import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.commandhandling.model.inspection.AnnotatedAggregate;
import org.axonframework.commandhandling.model.inspection.ModelInspector;
import org.axonframework.common.Registration;
import org.axonframework.common.caching.Cache;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.metadata.MetaData;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.matchers.CapturingMatcher;

import java.util.Arrays;
import java.util.Collections;

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
    private Aggregate<?> aggregate;

    private UnitOfWork<?> unitOfWork;

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
        aggregate = new AnnotatedAggregate<>(new StubAggregate(aggregateIdentifier),
                                             ModelInspector.inspectAggregate(StubAggregate.class), null);
        //noinspection unchecked
        mockCache = mock(Cache.class);
        listenerConfiguration = new CapturingMatcher<>();
        when(mockCache.registerCacheEntryListener(argThat(listenerConfiguration))).thenReturn(mock(Registration.class));

        unitOfWork = DefaultUnitOfWork.startAndGet(new GenericMessage<>("test"));
    }

    @After
    public void tearDown() {
        try {
            if (unitOfWork.isActive()) {
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
        readAllFrom(testSubject.decorateForRead(aggregateIdentifier, DomainEventStream.of(
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 0,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 1,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 2,
                                                "Mock contents", MetaData.emptyInstance())
        )));
        testSubject.decorateForAppend(aggregate, Arrays.asList(
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 3,
                                                "Mock contents",
                                                MetaData.emptyInstance())));

        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.rootType(), aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter).scheduleSnapshot(aggregate.rootType(), aggregateIdentifier);
    }

    @Test
    public void testSnapshotterNotTriggeredOnRead() {
        readAllFrom(testSubject.decorateForRead(aggregateIdentifier, DomainEventStream.of(
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 0,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 1,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 2,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 3,
                                                "Mock contents", MetaData.emptyInstance())
        )));

        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.getClass(), aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.getClass(), aggregateIdentifier);
    }

    @Test
    public void testSnapshotterNotTriggeredOnSave() {
        readAllFrom(testSubject.decorateForRead(aggregateIdentifier, DomainEventStream.of(
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 0,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 1,
                                                "Mock contents", MetaData.emptyInstance())
        )));
        testSubject.decorateForAppend(aggregate, Arrays.asList(
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 2,
                                                "Mock contents", MetaData.emptyInstance())));

        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.getClass(), aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.getClass(), aggregateIdentifier);
    }

    @Test
    public void testCounterDoesNotResetWhenUsingCache() {
        testSubject.setAggregateCache(mockCache);
        readAllFrom(testSubject.decorateForRead(aggregateIdentifier, DomainEventStream.of(
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 0,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 1,
                                                "Mock contents", MetaData.emptyInstance())
        )));
        testSubject.decorateForAppend(aggregate, Arrays.asList(
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 2,
                                                "Mock contents", MetaData.emptyInstance()
                )));
        testSubject.decorateForAppend(aggregate, Arrays.asList(
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 3,
                                                "Mock contents", MetaData.emptyInstance()
                )));

        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.rootType(), aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter).scheduleSnapshot(aggregate.rootType(), aggregateIdentifier);
    }

    @Test
    public void testCounterResetWhenCacheEvictsEntry() {
        testSubject.setAggregateCaches(Arrays.<Cache>asList(mockCache));
        readAllFrom(testSubject.decorateForRead(aggregateIdentifier, DomainEventStream.of(
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 0,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 1,
                                                "Mock contents", MetaData.emptyInstance())
        )));
        testSubject.decorateForAppend(aggregate, Arrays.asList(
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 2,
                                                "Mock contents", MetaData.emptyInstance())));

        final Cache.EntryListener listener = listenerConfiguration.getLastValue();
        listener.onEntryExpired(aggregateIdentifier);

        testSubject.decorateForAppend(aggregate, Arrays.asList(
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 3,
                                                "Mock contents", MetaData.emptyInstance())));

        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.getClass(), aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.getClass(), aggregateIdentifier);
    }

    @Test
    public void testCounterResetWhenCacheRemovesEntry() {
        testSubject.setAggregateCaches(Arrays.<Cache>asList(mockCache));
        readAllFrom(testSubject.decorateForRead(aggregateIdentifier, DomainEventStream.of(
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 0,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 1,
                                                "Mock contents", MetaData.emptyInstance())
        )));
        testSubject.decorateForAppend(aggregate, Arrays.asList(
                new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 2,
                                                "Mock contents", MetaData.emptyInstance())));

        final Cache.EntryListener listener = listenerConfiguration.getLastValue();
        listener.onEntryRemoved(aggregateIdentifier);

        testSubject.decorateForAppend(aggregate, Arrays.asList(new GenericDomainEventMessage<>("type", aggregateIdentifier,
                                                                                               (long) 3,
                                                                                               "Mock contents",
                                                                                               MetaData.emptyInstance())));


        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.getClass(), aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.getClass(), aggregateIdentifier);
    }

    @Test
    public void testStoringAggregateWithoutChanges() {
        testSubject.decorateForAppend(aggregate, Collections.emptyList());

        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.getClass(), aggregateIdentifier);
        CurrentUnitOfWork.commit();
    }

    private void readAllFrom(DomainEventStream events) {
        while (events.hasNext()) {
            events.next();
        }
    }
}
