/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GenericTrackedDomainEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.junit.*;
import org.mockito.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SequenceEventStorageEngineTest {
    private EventStorageEngine activeStorage;
    private EventStorageEngine historicStorage;
    private SequenceEventStorageEngine testSubject;

    @Before
    public void setUp() {
        activeStorage = mock(EventStorageEngine.class);
        historicStorage = mock(EventStorageEngine.class);
        testSubject = new SequenceEventStorageEngine(historicStorage, activeStorage);

        when(historicStorage.readSnapshot(anyString())).thenReturn(Optional.empty());
        when(activeStorage.readSnapshot(anyString())).thenReturn(Optional.empty());

    }

    @Test
    public void testPublishEventsSendsToActiveStorageOnly() {
        List<EventMessage<Object>> events = singletonList(GenericEventMessage.asEventMessage("test"));
        testSubject.appendEvents(events);

        verify(historicStorage, never()).appendEvents(anyList());
        verify(activeStorage).appendEvents(events);
    }

    @Test
    public void testAggregateEventsAreReadFromHistoricThenActive() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");
        DomainEventMessage<String> event2 = new GenericDomainEventMessage<>("type", "aggregate", 1, "test2");
        when(historicStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.of(event1));
        when(activeStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.of(event2));

        DomainEventStream actual = testSubject.readEvents("aggregate", 0);
        assertEquals(0L, (long) actual.getLastSequenceNumber());

        assertTrue(actual.hasNext());
        assertSame(event1, actual.peek());
        assertSame(event1, actual.next());
        verify(activeStorage, never()).readEvents(anyString(), anyLong());

        assertTrue(actual.hasNext());
        assertSame(event2, actual.peek());
        assertSame(event2, actual.next());
        assertEquals(1L, (long) actual.getLastSequenceNumber());

        InOrder inOrder = Mockito.inOrder(historicStorage, activeStorage);
        inOrder.verify(historicStorage).readEvents("aggregate", 0);
        inOrder.verify(activeStorage).readEvents("aggregate", 1);

        assertFalse(actual.hasNext());
    }

    @Test
    public void testAggregateEventsAreReadFromActiveWhenNoHistoricEventsAvailable() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");
        DomainEventMessage<String> event2 = new GenericDomainEventMessage<>("type", "aggregate", 1, "test2");
        when(historicStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.empty());
        when(activeStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.of(event1, event2));

        DomainEventStream actual = testSubject.readEvents("aggregate", 0);
        verify(activeStorage, never()).readEvents(anyString(), anyLong());

        assertSame(event1, actual.peek());
        assertSame(event1, actual.next());

        assertSame(event2, actual.peek());
        assertSame(event2, actual.next());

        InOrder inOrder = Mockito.inOrder(historicStorage, activeStorage);
        inOrder.verify(historicStorage).readEvents("aggregate", 0);
        inOrder.verify(activeStorage).readEvents("aggregate", 0);

        assertFalse(actual.hasNext());
    }

    @Test
    public void testSnapshotsStoredInActiveStorage() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");
        testSubject.storeSnapshot(event1);

        verify(activeStorage).storeSnapshot(event1);
        verify(historicStorage, never()).storeSnapshot(any());
    }

    @Test
    public void testEventStreamedFromHistoricThenActive() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");
        DomainEventMessage<String> event2 = new GenericDomainEventMessage<>("type", "aggregate", 1, "test2");
        TrackingToken token1 = new GlobalSequenceTrackingToken(1);
        TrackingToken token2 = new GlobalSequenceTrackingToken(2);

        TrackedEventMessage<?> trackedEvent1 = new GenericTrackedDomainEventMessage<>(token1, event1);
        TrackedEventMessage<?> trackedEvent2 = new GenericTrackedDomainEventMessage<>(token2, event2);

        doReturn(Stream.of(trackedEvent1)).when(historicStorage).readEvents(any(TrackingToken.class), anyBoolean());
        doReturn(Stream.of(trackedEvent2)).when(activeStorage).readEvents(any(TrackingToken.class), anyBoolean());

        GlobalSequenceTrackingToken startToken = new GlobalSequenceTrackingToken(0);
        Stream<? extends TrackedEventMessage<?>> actual = testSubject.readEvents(startToken, true);
        List<? extends TrackedEventMessage<?>> actualList = actual.collect(toList());

        assertEquals(2, actualList.size());
        assertEquals(Arrays.asList(trackedEvent1, trackedEvent2), actualList);

        verify(historicStorage).readEvents(startToken, true);
        verify(activeStorage).readEvents(token1, true);
    }

    @Test
    public void testSnapshotReadFromActiveThenHistoric() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");

        when(historicStorage.readSnapshot("aggregate")).thenReturn(Optional.of(event1));
        when(activeStorage.readSnapshot("aggregate")).thenReturn(Optional.empty());

        Optional<DomainEventMessage<?>> actual = testSubject.readSnapshot("aggregate");
        assertTrue(actual.isPresent());
        assertSame(event1, actual.get());

        InOrder inOrder = inOrder(historicStorage, activeStorage);
        inOrder.verify(activeStorage).readSnapshot("aggregate");
        inOrder.verify(historicStorage).readSnapshot("aggregate");
    }

    @Test
    public void testSnapshotReadFromActive() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");
        DomainEventMessage<String> event2 = new GenericDomainEventMessage<>("type", "aggregate", 1, "test2");

        when(historicStorage.readSnapshot("aggregate")).thenReturn(Optional.of(event2));
        when(activeStorage.readSnapshot("aggregate")).thenReturn(Optional.of(event1));

        Optional<DomainEventMessage<?>> actual = testSubject.readSnapshot("aggregate");
        assertTrue(actual.isPresent());
        assertSame(event1, actual.get());

        InOrder inOrder = inOrder(historicStorage, activeStorage);
        inOrder.verify(activeStorage).readSnapshot("aggregate");
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testCreateTailToken() {
        testSubject.createTailToken();

        verify(historicStorage).createTailToken();
    }

    @Test
    public void testCreateHeadToken() {
        testSubject.createHeadToken();

        verify(activeStorage).createHeadToken();
    }

    @Test
    public void testCreateTokenAtWhenIsPresentInActiveStorage() {
        Instant now = Instant.now();
        TrackingToken mockTrackingToken = new GlobalSequenceTrackingToken(3);
        when(activeStorage.createTokenAt(now)).thenReturn(mockTrackingToken);

        TrackingToken tokenAt = testSubject.createTokenAt(now);

        assertEquals(mockTrackingToken, tokenAt);
        verify(historicStorage, times(0)).createTokenAt(now);
    }

    @Test
    public void testCreateTokenAtWhenIsNotPresentInActiveStorage() {
        Instant now = Instant.now();
        TrackingToken mockTrackingToken = new GlobalSequenceTrackingToken(3);
        when(activeStorage.createTokenAt(now)).thenReturn(null);
        when(historicStorage.createTokenAt(now)).thenReturn(mockTrackingToken);

        TrackingToken tokenAt = testSubject.createTokenAt(now);

        assertEquals(mockTrackingToken, tokenAt);
    }
}
