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

package org.axonframework.eventstore.fs;

import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.DomainEventStream;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventsourcing.StubDomainEvent;
import org.axonframework.eventstore.EventStoreException;
import org.axonframework.commandhandling.model.ConflictingModificationException;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Matchers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author Allard Buijze
 */
public class FileSystemEventStoreTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    private String aggregateIdentifier;
    private File eventFileBaseDir;

    @Before
    public void setUp() {
        aggregateIdentifier = UUID.randomUUID().toString();
        eventFileBaseDir = tempFolder.getRoot();
    }

    @Test
    public void testSaveStreamAndReadBackIn() {
        FileSystemEventStore eventStore = new FileSystemEventStore(new SimpleEventFileResolver(eventFileBaseDir));

        GenericDomainEventMessage<StubDomainEvent> event1 = new GenericDomainEventMessage<>(
                aggregateIdentifier,
                0,
                new StubDomainEvent());
        GenericDomainEventMessage<StubDomainEvent> event2 = new GenericDomainEventMessage<>(
                aggregateIdentifier,
                1,
                new StubDomainEvent());
        GenericDomainEventMessage<StubDomainEvent> event3 = new GenericDomainEventMessage<>(
                aggregateIdentifier,
                2,
                new StubDomainEvent());
        eventStore.appendEvents(Arrays.asList(event1, event2, event3));

        DomainEventStream eventStream = eventStore.readEvents(aggregateIdentifier);
        List<DomainEventMessage<?>> domainEvents = new ArrayList<>();
        while (eventStream.hasNext()) {
            domainEvents.add(eventStream.next());
        }
        assertEquals(event1.getIdentifier(), domainEvents.get(0).getIdentifier());
        assertEquals(event2.getIdentifier(), domainEvents.get(1).getIdentifier());
        assertEquals(event3.getIdentifier(), domainEvents.get(2).getIdentifier());
    }

    @Test(expected = ConflictingModificationException.class)
    // Issue AXON-121: FileSystemEventStore allows duplicate construction of the same AggregateRoot
    public void testShouldThrowExceptionUponDuplicateAggregateId() {
        FileSystemEventStore eventStore = new FileSystemEventStore(new SimpleEventFileResolver(eventFileBaseDir));

        GenericDomainEventMessage<StubDomainEvent> event1 = new GenericDomainEventMessage<>(
                aggregateIdentifier,
                0,
                new StubDomainEvent());
        GenericDomainEventMessage<StubDomainEvent> event2 = new GenericDomainEventMessage<>(
                aggregateIdentifier,
                1,
                new StubDomainEvent());
        eventStore.appendEvents(Arrays.asList(event1, event2));

        GenericDomainEventMessage<StubDomainEvent> event3 = new GenericDomainEventMessage<>(
                aggregateIdentifier,
                0,
                new StubDomainEvent());
        eventStore.appendEvents(Arrays.asList(event3));
    }

    @Test
    public void testReadEventsWithIllegalSnapshot() {
        final XStreamSerializer serializer = spy(new XStreamSerializer());
        FileSystemEventStore eventStore = new FileSystemEventStore(serializer,
                                                                   new SimpleEventFileResolver(eventFileBaseDir));

        GenericDomainEventMessage<StubDomainEvent> event1 = new GenericDomainEventMessage<>(
                aggregateIdentifier,
                0,
                new StubDomainEvent());
        GenericDomainEventMessage<StubDomainEvent> event2 = new GenericDomainEventMessage<>(
                aggregateIdentifier,
                1,
                new StubDomainEvent());
        eventStore.appendEvents(Arrays.asList(event1, event2));

        doReturn(new SimpleSerializedObject<>("error".getBytes(), byte[].class, String.class.getName(), "old"))
                .when(serializer).serialize(anyObject(), eq(byte[].class));
        eventStore.appendSnapshotEvent(event2);

        DomainEventStream actual = eventStore.readEvents(aggregateIdentifier);
        assertTrue(actual.hasNext());
        assertEquals(0, actual.next().getSequenceNumber());
        assertEquals(1, actual.next().getSequenceNumber());
        assertFalse(actual.hasNext());
    }

    @Test
    // Issue #25: XStreamFileSystemEventStore fails when event data contains newline character
    public void testSaveStreamAndReadBackIn_NewLineInEvent() {
        FileSystemEventStore eventStore = new FileSystemEventStore(new SimpleEventFileResolver(eventFileBaseDir));

        String description = "This is a description with a \n newline character and weird chars éçè\u6324.";
        StringBuilder stringBuilder = new StringBuilder(description);
        for (int i = 0; i < 100; i++) {
            stringBuilder.append(
                    "Some more text to make this event really long. It should not be a problem for the event serializer.");
        }
        description = stringBuilder.toString();
        GenericDomainEventMessage<MyStubDomainEvent> event1 = new GenericDomainEventMessage<>(
                aggregateIdentifier,
                0,
                new MyStubDomainEvent(description));
        GenericDomainEventMessage<StubDomainEvent> event2 = new GenericDomainEventMessage<>(
                aggregateIdentifier,
                1,
                new StubDomainEvent());

        eventStore.appendEvents(Arrays.asList(event1, event2));

        DomainEventStream eventStream = eventStore.readEvents(aggregateIdentifier);
        List<DomainEventMessage<?>> domainEvents = new ArrayList<>();
        while (eventStream.hasNext()) {
            domainEvents.add(eventStream.next());
        }
        MyStubDomainEvent actualEvent1 = (MyStubDomainEvent) domainEvents.get(0).getPayload();
        assertEquals(description, actualEvent1.getDescription());
        assertEquals(event2.getPayloadType(), domainEvents.get(1).getPayloadType());
        assertEquals(event2.getIdentifier(), domainEvents.get(1).getIdentifier());
    }

    @Test
    public void testRead_FileNotReadable() throws IOException {
        EventFileResolver mockEventFileResolver = mock(EventFileResolver.class);
        InputStream mockInputStream = mock(InputStream.class);
        when(mockEventFileResolver.eventFileExists(any())).thenReturn(true);
        when(mockEventFileResolver.openEventFileForReading(any()))
                .thenReturn(mockInputStream);
        IOException exception = new IOException("Mock Exception");
        when(mockInputStream.read()).thenThrow(exception);
        when(mockInputStream.read(Matchers.<byte[]>any())).thenThrow(exception);
        when(mockInputStream.read(Matchers.<byte[]>any(), anyInt(), anyInt())).thenThrow(exception);
        FileSystemEventStore eventStore = new FileSystemEventStore(mockEventFileResolver);

        try {
            eventStore.readEvents(UUID.randomUUID().toString());
            fail("Expected an exception");
        } catch (EventStoreException e) {
            assertSame(exception, e.getCause());
        }
    }

    @Test
    public void testWrite_FileDoesNotExist() throws IOException {
        String aggregateIdentifier = "aggregateIdentifier";
        IOException exception = new IOException("Mock");
        EventFileResolver mockEventFileResolver = mock(EventFileResolver.class);
        when(mockEventFileResolver.openEventFileForWriting(isA(String.class)))
                .thenThrow(exception);
        FileSystemEventStore eventStore = new FileSystemEventStore(mockEventFileResolver);

        GenericDomainEventMessage<StubDomainEvent> event1 = new GenericDomainEventMessage<>(
                aggregateIdentifier,
                0,
                new StubDomainEvent());
        GenericDomainEventMessage<StubDomainEvent> event2 = new GenericDomainEventMessage<>(
                aggregateIdentifier,
                1,
                new StubDomainEvent());
        GenericDomainEventMessage<StubDomainEvent> event3 = new GenericDomainEventMessage<>(
                aggregateIdentifier,
                2,
                new StubDomainEvent());

        try {
            eventStore.appendEvents(Arrays.asList(event1, event2, event3));
            fail("Expected an exception");
        } catch (EventStoreException e) {
            assertEquals(exception, e.getCause());
        }
    }

    @Test
    public void testAppendSnapShot() {
        FileSystemEventStore eventStore = new FileSystemEventStore(new SimpleEventFileResolver(eventFileBaseDir));

        AtomicInteger counter = new AtomicInteger(0);

        GenericDomainEventMessage<StubDomainEvent> snapshot1 = new GenericDomainEventMessage<>(
                aggregateIdentifier,
                4,
                new StubDomainEvent());
        GenericDomainEventMessage<StubDomainEvent> snapshot2 = new GenericDomainEventMessage<>(
                aggregateIdentifier,
                9,
                new StubDomainEvent());
        GenericDomainEventMessage<StubDomainEvent> snapshot3 = new GenericDomainEventMessage<>(
                aggregateIdentifier,
                14,
                new StubDomainEvent());

        writeEvents(counter, 5);
        eventStore.appendSnapshotEvent(snapshot1);
        writeEvents(counter, 5);
        eventStore.appendSnapshotEvent(snapshot2);
        writeEvents(counter, 5);
        eventStore.appendSnapshotEvent(snapshot3);
        writeEvents(counter, 2);

        DomainEventStream eventStream = eventStore.readEvents(aggregateIdentifier);
        List<DomainEventMessage<?>> actualEvents = new ArrayList<>();
        while (eventStream.hasNext()) {
            actualEvents.add(eventStream.next());
        }
        assertEquals(14L, actualEvents.get(0).getSequenceNumber());
        assertEquals(3, actualEvents.size());
    }

    private void writeEvents(AtomicInteger counter, int numberOfEvents) {
        FileSystemEventStore eventStore = new FileSystemEventStore(new SimpleEventFileResolver(eventFileBaseDir));

        DomainEventMessage[] events = new DomainEventMessage[numberOfEvents];
        for (int t = 0; t < numberOfEvents; t++) {
            events[t] = new GenericDomainEventMessage<>(aggregateIdentifier, counter.getAndIncrement(),
                                                        new StubDomainEvent());
        }
        eventStore.appendEvents(Arrays.asList(events));
    }

    public static class MyStubDomainEvent extends StubDomainEvent {

        private static final long serialVersionUID = -7959231436742664073L;
        private final String description;

        public MyStubDomainEvent(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
