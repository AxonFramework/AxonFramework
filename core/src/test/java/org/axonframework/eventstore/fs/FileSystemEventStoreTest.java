/*
 * Copyright (c) 2010-2011. Axon Framework
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

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventstore.EventStoreException;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.junit.*;
import org.mockito.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class FileSystemEventStoreTest {

    private Object aggregateIdentifier;
    private File eventFileBaseDir;

    @Before
    public void setUp() {
        aggregateIdentifier = UUID.randomUUID();
        eventFileBaseDir = new File("target/");
    }

    @Test
    public void testSaveStreamAndReadBackIn() {
        FileSystemEventStore eventStore = new FileSystemEventStore(new SimpleEventFileResolver(eventFileBaseDir));

        GenericDomainEventMessage<StubDomainEvent> event1 = new GenericDomainEventMessage<StubDomainEvent>(
                aggregateIdentifier,
                0,
                new StubDomainEvent());
        GenericDomainEventMessage<StubDomainEvent> event2 = new GenericDomainEventMessage<StubDomainEvent>(
                aggregateIdentifier,
                1,
                new StubDomainEvent());
        GenericDomainEventMessage<StubDomainEvent> event3 = new GenericDomainEventMessage<StubDomainEvent>(
                aggregateIdentifier,
                2,
                new StubDomainEvent());
        DomainEventStream stream = new SimpleDomainEventStream(event1, event2, event3);
        eventStore.appendEvents("test", stream);

        DomainEventStream eventStream = eventStore.readEvents("test", aggregateIdentifier);
        List<DomainEventMessage<?>> domainEvents = new ArrayList<DomainEventMessage<?>>();
        while (eventStream.hasNext()) {
            domainEvents.add(eventStream.next());
        }
        assertEquals(event1.getIdentifier(), domainEvents.get(0).getIdentifier());
        assertEquals(event2.getIdentifier(), domainEvents.get(1).getIdentifier());
        assertEquals(event3.getIdentifier(), domainEvents.get(2).getIdentifier());
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
        GenericDomainEventMessage<MyStubDomainEvent> event1 = new GenericDomainEventMessage<MyStubDomainEvent>(
                aggregateIdentifier,
                0,
                new MyStubDomainEvent(description));
        GenericDomainEventMessage<StubDomainEvent> event2 = new GenericDomainEventMessage<StubDomainEvent>(
                aggregateIdentifier,
                1,
                new StubDomainEvent());

        DomainEventStream stream = new SimpleDomainEventStream(event1, event2);
        eventStore.appendEvents("test", stream);

        DomainEventStream eventStream = eventStore.readEvents("test", aggregateIdentifier);
        List<DomainEventMessage<? extends Object>> domainEvents = new ArrayList<DomainEventMessage<? extends Object>>();
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
        when(mockEventFileResolver.eventFileExists(isA(String.class), any())).thenReturn(true);
        when(mockEventFileResolver.openEventFileForReading(isA(String.class), any()))
                .thenReturn(mockInputStream);
        IOException exception = new IOException("Mock Exception");
        when(mockInputStream.read()).thenThrow(exception);
        when(mockInputStream.read(Matchers.<byte[]>any())).thenThrow(exception);
        when(mockInputStream.read(Matchers.<byte[]>any(), anyInt(), anyInt())).thenThrow(exception);
        FileSystemEventStore eventStore = new FileSystemEventStore(mockEventFileResolver);

        try {
            eventStore.readEvents("test", UUID.randomUUID());
            fail("Expected an exception");
        } catch (EventStoreException e) {
            assertSame(exception, e.getCause());
        }
    }

    @Test
    public void testWrite_FileDoesNotExist() throws IOException {
        Object aggregateIdentifier = "aggregateIdentifier";
        IOException exception = new IOException("Mock");
        EventFileResolver mockEventFileResolver = mock(EventFileResolver.class);
        when(mockEventFileResolver.openEventFileForWriting(isA(String.class), isA(Object.class)))
                .thenThrow(exception);
        FileSystemEventStore eventStore = new FileSystemEventStore(mockEventFileResolver);

        GenericDomainEventMessage<StubDomainEvent> event1 = new GenericDomainEventMessage<StubDomainEvent>(
                aggregateIdentifier,
                0,
                new StubDomainEvent());
        GenericDomainEventMessage<StubDomainEvent> event2 = new GenericDomainEventMessage<StubDomainEvent>(
                aggregateIdentifier,
                1,
                new StubDomainEvent());
        GenericDomainEventMessage<StubDomainEvent> event3 = new GenericDomainEventMessage<StubDomainEvent>(
                aggregateIdentifier,
                2,
                new StubDomainEvent());
        DomainEventStream stream = new SimpleDomainEventStream(event1, event2, event3);

        try {
            eventStore.appendEvents("test", stream);
            fail("Expected an exception");
        } catch (EventStoreException e) {
            assertEquals(exception, e.getCause());
        }
    }

    @Test
    public void testAppendSnapShot() {
        FileSystemEventStore eventStore = new FileSystemEventStore(new SimpleEventFileResolver(eventFileBaseDir));

        AtomicInteger counter = new AtomicInteger(0);

        GenericDomainEventMessage<StubDomainEvent> snapshot1 = new GenericDomainEventMessage<StubDomainEvent>(
                aggregateIdentifier,
                4,
                new StubDomainEvent());
        GenericDomainEventMessage<StubDomainEvent> snapshot2 = new GenericDomainEventMessage<StubDomainEvent>(
                aggregateIdentifier,
                9,
                new StubDomainEvent());
        GenericDomainEventMessage<StubDomainEvent> snapshot3 = new GenericDomainEventMessage<StubDomainEvent>(
                aggregateIdentifier,
                14,
                new StubDomainEvent());

        writeEvents(counter, 5);
        eventStore.appendSnapshotEvent("snapshotting", snapshot1);
        writeEvents(counter, 5);
        eventStore.appendSnapshotEvent("snapshotting", snapshot2);
        writeEvents(counter, 5);
        eventStore.appendSnapshotEvent("snapshotting", snapshot3);
        writeEvents(counter, 2);

        DomainEventStream eventStream = eventStore.readEvents("snapshotting", aggregateIdentifier);
        List<DomainEventMessage<? extends Object>> actualEvents = new ArrayList<DomainEventMessage<? extends Object>>();
        while (eventStream.hasNext()) {
            actualEvents.add(eventStream.next());
        }
        assertEquals(14L, actualEvents.get(0).getSequenceNumber());
        assertEquals(3, actualEvents.size());
    }

    private void writeEvents(AtomicInteger counter, int numberOfEvents) {
        FileSystemEventStore eventStore = new FileSystemEventStore(new SimpleEventFileResolver(eventFileBaseDir));

        List<DomainEventMessage> events = new ArrayList<DomainEventMessage>();
        for (int t = 0; t < numberOfEvents; t++) {
            GenericDomainEventMessage<StubDomainEvent> event = new GenericDomainEventMessage<StubDomainEvent>(
                    aggregateIdentifier,
                    counter.getAndIncrement(),
                    new StubDomainEvent());
            events.add(event);
        }
        eventStore.appendEvents("snapshotting", new SimpleDomainEventStream(events));
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
