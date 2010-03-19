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

package org.axonframework.core.repository.eventsourcing;

import org.axonframework.core.AggregateNotFoundException;
import org.axonframework.core.DomainEvent;
import org.axonframework.core.DomainEventStream;
import org.axonframework.core.SimpleDomainEventStream;
import org.axonframework.core.StubDomainEvent;
import org.junit.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class FileSystemEventStoreTest {

    private FileSystemEventStore eventStore;

    @Before
    public void setUp() {
        eventStore = new FileSystemEventStore(new XStreamEventSerializer());
        eventStore.setBaseDir(new FileSystemResource("target/"));
    }

    @Test
    public void testSaveStreamAndReadBackIn() {
        UUID aggregateId = UUID.randomUUID();
        StubDomainEvent event1 = new StubDomainEvent(aggregateId, 0);
        StubDomainEvent event2 = new StubDomainEvent(aggregateId, 1);
        StubDomainEvent event3 = new StubDomainEvent(aggregateId, 2);
        DomainEventStream stream = new SimpleDomainEventStream(event1, event2, event3);
        eventStore.appendEvents("test", stream);

        DomainEventStream eventStream = eventStore.readEvents("test", aggregateId);
        List<DomainEvent> domainEvents = new ArrayList<DomainEvent>();
        while (eventStream.hasNext()) {
            domainEvents.add(eventStream.next());
        }
        assertEquals(event1, domainEvents.get(0));
        assertEquals(event2, domainEvents.get(1));
        assertEquals(event3, domainEvents.get(2));
    }

    @Test
    public void testRead_WithSnapshotEvent() {
        UUID aggregateId = UUID.fromString("fab0c2ec-a759-40f1-9078-9defb16e8553");
        StubDomainEvent event2 = new StubDomainEvent(aggregateId, 1);
        StubDomainEvent event3 = new StubDomainEvent(aggregateId, 2);
        eventStore.setBaseDir(new ClassPathResource("/snapshots/"));
        DomainEventStream eventStream = eventStore.readEvents("events", aggregateId);
        List<DomainEvent> domainEvents = new ArrayList<DomainEvent>();
        while (eventStream.hasNext()) {
            domainEvents.add(eventStream.next());
        }

        assertEquals(event2.getSequenceNumber(), domainEvents.get(0).getSequenceNumber());
        assertEquals(event2.getAggregateIdentifier(), domainEvents.get(0).getAggregateIdentifier());
        assertEquals(event2.getClass(), domainEvents.get(0).getClass());
        assertEquals(event3.getSequenceNumber(), domainEvents.get(1).getSequenceNumber());
        assertEquals(event3.getAggregateIdentifier(), domainEvents.get(1).getAggregateIdentifier());
        assertEquals(event3.getClass(), domainEvents.get(1).getClass());
    }

    @Test
    // Issue #25: XStreamFileSystemEventStore fails when event data contains newline character
    public void testSaveStreamAndReadBackIn_NewLineInEvent() {
        UUID aggregateId = UUID.randomUUID();
        String description = "This is a description with a \n newline character and weird chars éçè\u6324.";
        StringBuilder stringBuilder = new StringBuilder(description);
        for (int i = 0; i < 100; i++) {
            stringBuilder.append(
                    "Some more text to make this event really long. It should not be a problem for the event serializer.");
        }
        description = stringBuilder.toString();
        MyStubDomainEvent event1 = new MyStubDomainEvent(aggregateId, 0, description);
        StubDomainEvent event2 = new StubDomainEvent(aggregateId, 1);
        DomainEventStream stream = new SimpleDomainEventStream(event1, event2);
        eventStore.appendEvents("test", stream);

        DomainEventStream eventStream = eventStore.readEvents("test", aggregateId);
        List<DomainEvent> domainEvents = new ArrayList<DomainEvent>();
        while (eventStream.hasNext()) {
            domainEvents.add(eventStream.next());
        }
        MyStubDomainEvent actualEvent1 = (MyStubDomainEvent) domainEvents.get(0);
        assertEquals(event1, actualEvent1);
        assertEquals(description, actualEvent1.getDescription());
        assertEquals(event2, domainEvents.get(1));
    }

    @Test
    public void testRead_FileNotReadable() throws IOException {
        Resource mockResource = mock(Resource.class);
        InputStream mockInputStream = mock(InputStream.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.createRelative(isA(String.class))).thenReturn(mockResource);
        IOException exception = new IOException("Mock exception");
        when(mockResource.getInputStream()).thenThrow(exception);
        eventStore.setBaseDir(mockResource);

        try {
            eventStore.readEvents("test", UUID.randomUUID());
            fail("Expected an exception");
        }
        catch (EventStoreException e) {
            assertSame(exception, e.getCause());
        }
    }

    @Test
    public void testRead_FileDoesNotExist() throws IOException {
        UUID aggregateId = UUID.randomUUID();
        Resource mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true, false); // true for dir, false for file
        when(mockResource.createRelative(isA(String.class))).thenReturn(mockResource);
        eventStore.setBaseDir(mockResource);

        try {
            eventStore.readEvents("test", aggregateId);
            fail("Expected an exception");
        }
        catch (AggregateNotFoundException e) {
            assertTrue(e.getMessage().contains(aggregateId.toString()));
        }
    }

    @Test
    public void testWrite_FileDoesNotExist() throws IOException {
        UUID aggregateId = UUID.randomUUID();
        Resource mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true, false); // true for dir, false for file
        when(mockResource.createRelative(isA(String.class))).thenReturn(mockResource);
        IOException exception = new IOException("Mock");
        when(mockResource.getFile()).thenThrow(exception);
        eventStore.setBaseDir(mockResource);

        StubDomainEvent event1 = new StubDomainEvent(aggregateId, 0);
        StubDomainEvent event2 = new StubDomainEvent(aggregateId, 1);
        StubDomainEvent event3 = new StubDomainEvent(aggregateId, 2);
        DomainEventStream stream = new SimpleDomainEventStream(event1, event2, event3);

        try {
            eventStore.appendEvents("test", stream);
            fail("Expected an exception");
        }
        catch (EventStoreException e) {
            assertEquals(exception, e.getCause());
        }
    }

    @Test
    public void testWrite_DirectoryCannotBeCreated() throws IOException {
        UUID aggregateId = UUID.randomUUID();
        Resource mockResource = mock(Resource.class);
        File mockFile = mock(File.class);
        when(mockResource.exists()).thenReturn(false);
        when(mockResource.getFile()).thenReturn(mockFile);
        when(mockFile.mkdirs()).thenReturn(false);
        when(mockResource.createRelative(isA(String.class))).thenReturn(mockResource);

        eventStore.setBaseDir(mockResource);

        StubDomainEvent event1 = new StubDomainEvent(aggregateId, 0);
        StubDomainEvent event2 = new StubDomainEvent(aggregateId, 1);
        StubDomainEvent event3 = new StubDomainEvent(aggregateId, 2);
        DomainEventStream stream = new SimpleDomainEventStream(event1, event2, event3);

        try {
            eventStore.appendEvents("test", stream);
            fail("Expected an exception");
        }
        catch (EventStoreException e) {
            assertTrue(e.getMessage().contains("could not be created"));
        }
    }

    public static class MyStubDomainEvent extends StubDomainEvent {

        private final String description;

        public MyStubDomainEvent(UUID aggregateId, int sequenceNumber, String description) {
            super(aggregateId, sequenceNumber);
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
