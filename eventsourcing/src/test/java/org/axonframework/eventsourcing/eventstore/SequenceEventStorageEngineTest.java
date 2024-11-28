/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.axonframework.messaging.QualifiedNameUtils.dottedName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SequenceEventStorageEngine}.
 *
 * @author Allard Buijze
 */
class SequenceEventStorageEngineTest {

    private EventStorageEngine activeStorage;
    private EventStorageEngine historicStorage;

    private SequenceEventStorageEngine testSubject;

    @BeforeEach
    void setUp() {
        activeStorage = mock(EventStorageEngine.class, "activeStorage");
        historicStorage = mock(EventStorageEngine.class, "historicStorage");
        testSubject = new SequenceEventStorageEngine(historicStorage, activeStorage);

        when(historicStorage.readSnapshot(anyString())).thenReturn(Optional.empty());
        when(activeStorage.readSnapshot(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void publishEventsSendsToActiveStorageOnly() {
        List<EventMessage<Object>> events = singletonList(GenericEventMessage.asEventMessage("test"));
        testSubject.appendEvents(events);

        verify(historicStorage, never()).appendEvents(anyList());
        verify(activeStorage).appendEvents(events);
    }

    @Test
    void aggregateEventsAreReadFromHistoricThenActive() {
        DomainEventMessage<String> event1 =
                new GenericDomainEventMessage<>("type", "aggregate", 0, dottedName("test.event"), "test1");
        DomainEventMessage<String> event2 =
                new GenericDomainEventMessage<>("type", "aggregate", 1, dottedName("test.event"), "test2");
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
    void aggregateEventsAreReadFromActiveWhenNoHistoricEventsAvailable() {
        DomainEventMessage<String> event1 =
                new GenericDomainEventMessage<>("type", "aggregate", 0, dottedName("test.event"), "test1");
        DomainEventMessage<String> event2 =
                new GenericDomainEventMessage<>("type", "aggregate", 1, dottedName("test.event"), "test2");
        when(historicStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.empty());
        when(activeStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.of(event1, event2));

        DomainEventStream actual = testSubject.readEvents("aggregate", 0);
        verify(activeStorage, never()).readEvents(anyString(), anyLong());

        assertSame(event1, actual.peek());
        assertSame(event1, actual.next());

        assertSame(event2, actual.peek());
        assertSame(event2, actual.next());

        Long seq = actual.getLastSequenceNumber();
        assertNotNull(seq);

        InOrder inOrder = Mockito.inOrder(historicStorage, activeStorage);
        inOrder.verify(historicStorage).readEvents("aggregate", 0);
        inOrder.verify(activeStorage).readEvents("aggregate", 0);

        assertFalse(actual.hasNext());
    }

    @Test
    void aggregateEventsAreReadFromHistoricWhenNoActiveEventsAvailable() {
        DomainEventMessage<String> event1 =
                new GenericDomainEventMessage<>("type", "aggregate", 0, dottedName("test.event"), "test1");
        DomainEventMessage<String> event2 =
                new GenericDomainEventMessage<>("type", "aggregate", 1, dottedName("test.event"), "test2");
        when(historicStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.of(event1, event2));
        when(activeStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.empty());

        DomainEventStream actual = testSubject.readEvents("aggregate", 0);
        assertEquals(1L, (long) actual.getLastSequenceNumber());
        verify(activeStorage, never()).readEvents(anyString(), anyLong());

        assertSame(event1, actual.peek());
        assertSame(event1, actual.next());

        assertSame(event2, actual.peek());
        assertSame(event2, actual.next());

        verify(activeStorage, never()).readEvents(anyString(), anyLong());

        assertFalse(actual.hasNext()); // this initializes second domain stream
        verify(activeStorage).readEvents("aggregate", 2);
        assertEquals(1L, (long) actual.getLastSequenceNumber());

        InOrder inOrder = Mockito.inOrder(historicStorage, activeStorage);
        inOrder.verify(historicStorage).readEvents("aggregate", 0);
        inOrder.verify(activeStorage).readEvents("aggregate", 2);

        assertFalse(actual.hasNext());
    }

    @Test
    void snapshotsStoredInActiveStorage() {
        DomainEventMessage<String> event1 =
                new GenericDomainEventMessage<>("type", "aggregate", 0, dottedName("test.event"), "test1");
        testSubject.storeSnapshot(event1);

        verify(activeStorage).storeSnapshot(event1);
        verify(historicStorage, never()).storeSnapshot(any());
    }

    @Test
    void eventStreamedFromHistoricThenActive() {
        DomainEventMessage<String> event1 =
                new GenericDomainEventMessage<>("type", "aggregate", 0, dottedName("test.event"), "test1");
        DomainEventMessage<String> event2 =
                new GenericDomainEventMessage<>("type", "aggregate", 1, dottedName("test.event"), "test2");
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
    void snapshotReadFromActiveThenHistoric() {
        DomainEventMessage<String> event1 =
                new GenericDomainEventMessage<>("type", "aggregate", 0, dottedName("test.event"), "test1");

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
    void snapshotReadFromActive() {
        DomainEventMessage<String> event1 =
                new GenericDomainEventMessage<>("type", "aggregate", 0, dottedName("test.event"), "test1");
        DomainEventMessage<String> event2 =
                new GenericDomainEventMessage<>("type", "aggregate", 1, dottedName("test.event"), "test2");

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
    void createTailToken() {
        testSubject.createTailToken();

        verify(historicStorage).createTailToken();
    }

    @Test
    void createHeadToken() {
        testSubject.createHeadToken();

        verify(activeStorage).createHeadToken();
    }

    @Test
    void createTokenAtWhenIsPresentInActiveStorage() {
        Instant now = Instant.now();
        TrackingToken mockTrackingToken = new GlobalSequenceTrackingToken(3);
        when(activeStorage.createTokenAt(now)).thenReturn(mockTrackingToken);

        TrackingToken tokenAt = testSubject.createTokenAt(now);

        assertEquals(mockTrackingToken, tokenAt);
        verify(historicStorage, times(0)).createTokenAt(now);
    }

    @Test
    void createTokenAtWhenIsNotPresentInActiveStorage() {
        Instant now = Instant.now();
        TrackingToken mockTrackingToken = new GlobalSequenceTrackingToken(3);
        when(activeStorage.createTokenAt(now)).thenReturn(null);
        when(historicStorage.createTokenAt(now)).thenReturn(mockTrackingToken);

        TrackingToken tokenAt = testSubject.createTokenAt(now);

        assertEquals(mockTrackingToken, tokenAt);
    }

    @Test
    void streamFromPositionInActiveStorage() {
        historicStorage = new InMemoryEventStorageEngine();
        activeStorage = new InMemoryEventStorageEngine(1);

        testSubject = new SequenceEventStorageEngine(historicStorage, activeStorage);

        DomainEventMessage<String> event1 =
                new GenericDomainEventMessage<>("type", "aggregate", 0, dottedName("test.event"), "test1");
        DomainEventMessage<String> event2 =
                new GenericDomainEventMessage<>("type", "aggregate", 1, dottedName("test.event"), "test2");
        DomainEventMessage<String> event3 =
                new GenericDomainEventMessage<>("type", "aggregate", 2, dottedName("test.event"), "test3");
        historicStorage.appendEvents(event1);

        activeStorage.appendEvents(event2, event3);

        Stream<? extends TrackedEventMessage<?>> stream = testSubject.readEvents(null, true);
        TrackedEventMessage<?> firstEvent = stream.findFirst().orElseThrow(IllegalStateException::new);
        assertEquals("test1", firstEvent.getPayload());

        Stream<? extends TrackedEventMessage<?>> stream2 = testSubject.readEvents(firstEvent.trackingToken(), true);
        List<TrackedEventMessage<?>> secondBatch = stream2.collect(toList());
        assertEquals(2, secondBatch.size());
        assertEquals("test2", secondBatch.get(0).getPayload());
        assertEquals("test3", secondBatch.get(1).getPayload());

        Stream<? extends TrackedEventMessage<?>> stream3 =
                testSubject.readEvents(secondBatch.get(0).trackingToken(), true);
        List<TrackedEventMessage<?>> thirdBatch = stream3.collect(toList());
        assertEquals(1, thirdBatch.size());
        assertEquals("test3", thirdBatch.get(0).getPayload());

        Stream<? extends TrackedEventMessage<?>> stream4 =
                testSubject.readEvents(secondBatch.get(1).trackingToken(), true);
        assertFalse(stream4.findFirst().isPresent());
    }

    @Test
    void aggregateEventsAreReadFromFirstSequenceNumber() {
        DomainEventMessage<String> event1 =
                new GenericDomainEventMessage<>("type", "aggregate", 0, dottedName("test.event"), "test1");
        DomainEventMessage<String> snapshotEvent =
                new GenericDomainEventMessage<>("type", "aggregate", 1, dottedName("test.snapshot"), "test2");
        DomainEventMessage<String> event3 =
                new GenericDomainEventMessage<>("type", "aggregate", 2, dottedName("test.event"), "test3");

        when(historicStorage.readEvents(eq("aggregate"), eq(0L))).thenReturn(DomainEventStream.of(event1));
        when(historicStorage.readEvents(eq("aggregate"), longThat(l -> l > 0))).thenReturn(DomainEventStream.empty());

        when(activeStorage.readEvents(eq("aggregate"), longThat(l -> l < 2)))
                .thenReturn(DomainEventStream.of(snapshotEvent, event3));
        when(activeStorage.readEvents(eq("aggregate"), eq(2L))).thenReturn(DomainEventStream.of(event3));
        when(activeStorage.readEvents(eq("aggregate"), longThat(l -> l > 2))).thenReturn(DomainEventStream.empty());

        // Emulate readEvents from AbstractEventStore.readEvents(java.lang.String) after snapshot
        DomainEventStream actual = testSubject.readEvents("aggregate", 2);

        verify(activeStorage, never()).readEvents(anyString(), anyLong());

        assertTrue(actual.hasNext());
        assertSame(event3, actual.peek());
        assertSame(event3, actual.next());
        assertEquals(2L, (long) actual.getLastSequenceNumber());

        assertFalse(actual.hasNext());

        InOrder inOrder = Mockito.inOrder(historicStorage, activeStorage);
        inOrder.verify(historicStorage).readEvents("aggregate", 2);
        inOrder.verify(activeStorage).readEvents("aggregate", 2);
    }

    @Test
    void aggregateEventsAreReadFromFirstSequenceNumberHistoricOnly() {
        DomainEventMessage<String> event1 =
                new GenericDomainEventMessage<>("type", "aggregate", 0, dottedName("test.event"), "test1");
        DomainEventMessage<String> snapshotEvent =
                new GenericDomainEventMessage<>("type", "aggregate", 1, dottedName("test.snapshot"), "test2");
        DomainEventMessage<String> event3 =
                new GenericDomainEventMessage<>("type", "aggregate", 2, dottedName("test.event"), "test3");

        when(historicStorage.readEvents(eq("aggregate"), eq(0L)))
                .thenReturn(DomainEventStream.of(event1, snapshotEvent, event3));
        when(historicStorage.readEvents(eq("aggregate"), eq(1L)))
                .thenReturn(DomainEventStream.of(snapshotEvent, event3));
        when(historicStorage.readEvents(eq("aggregate"), eq(2L))).thenReturn(DomainEventStream.of(event3));
        when(historicStorage.readEvents(eq("aggregate"), longThat(l -> l > 2))).thenReturn(DomainEventStream.empty());

        when(activeStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.empty());

        // Emulate readEvents from AbstractEventStore.readEvents(java.lang.String) after snapshot
        DomainEventStream actual = testSubject.readEvents("aggregate", 2);

        verify(activeStorage, never()).readEvents(anyString(), anyLong());

        assertTrue(actual.hasNext());
        assertSame(event3, actual.peek());
        assertSame(event3, actual.next());
        assertEquals(2L, (long) actual.getLastSequenceNumber());

        assertFalse(actual.hasNext());

        InOrder inOrder = Mockito.inOrder(historicStorage, activeStorage);
        inOrder.verify(historicStorage).readEvents("aggregate", 2);
        inOrder.verify(activeStorage).readEvents("aggregate", 3);
    }

    @Test
    void aggregateEventsAreReadFromFirstSequenceNumberActiveOnly() {
        DomainEventMessage<String> event1 =
                new GenericDomainEventMessage<>("type", "aggregate", 0, dottedName("test.event"), "test1");
        DomainEventMessage<String> snapshotEvent =
                new GenericDomainEventMessage<>("type", "aggregate", 1, dottedName("test.snapshot"), "test3");
        DomainEventMessage<String> event3 =
                new GenericDomainEventMessage<>("type", "aggregate", 2, dottedName("test.event"), "test4");

        when(historicStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.empty());

        when(activeStorage.readEvents(eq("aggregate"), eq(0L)))
                .thenReturn(DomainEventStream.of(event1, snapshotEvent, event3));
        when(activeStorage.readEvents(eq("aggregate"), eq(1L))).thenReturn(DomainEventStream.of(snapshotEvent, event3));
        when(activeStorage.readEvents(eq("aggregate"), eq(2L))).thenReturn(DomainEventStream.of(event3));
        when(activeStorage.readEvents(eq("aggregate"), longThat(l -> l > 2))).thenReturn(DomainEventStream.empty());

        // Emulate readEvents from AbstractEventStore.readEvents(java.lang.String) after snapshot
        DomainEventStream actual = testSubject.readEvents("aggregate", 2);

        verify(activeStorage, never()).readEvents(anyString(), anyLong());

        assertTrue(actual.hasNext());
        assertSame(event3, actual.peek());
        assertSame(event3, actual.next());
        assertEquals(2L, (long) actual.getLastSequenceNumber());

        assertFalse(actual.hasNext());

        InOrder inOrder = Mockito.inOrder(historicStorage, activeStorage);
        inOrder.verify(historicStorage).readEvents("aggregate", 2);
        inOrder.verify(activeStorage).readEvents("aggregate", 2);
    }
}
