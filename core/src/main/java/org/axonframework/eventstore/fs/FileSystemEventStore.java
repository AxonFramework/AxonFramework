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

import org.axonframework.common.io.IOUtils;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventStoreException;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.io.EventMessageReader;
import org.axonframework.io.EventMessageWriter;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.xml.XStreamSerializer;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;

/**
 * Implementation of the {@link org.axonframework.eventstore.EventStore} that serializes objects (by default using XStream) and
 * writes them to files to disk. Each aggregate is represented by a single file.
 * <p/>
 * Use {@link EventFileResolver} to specify the directory where event files should be stored and written to.
 * <p/>
 * Note that the resource supplied must point to a folder and should contain a trailing slash. See {@link
 * org.springframework.core.io.FileSystemResource#FileSystemResource(String)}.
 *
 * @author Allard Buijze
 * @author Frank Versnel
 * @since 0.5
 */
public class FileSystemEventStore implements EventStore, SnapshotEventStore {

    private final Serializer eventSerializer;
    private final EventFileResolver eventFileResolver;

    /**
     * Basic initialization of the event store. The actual serialization and deserialization is delegated to a {@link
     * org.axonframework.serializer.xml.XStreamSerializer}
     *
     * @param eventFileResolver The EventFileResolver providing access to event files
     */
    public FileSystemEventStore(EventFileResolver eventFileResolver) {
        this(new XStreamSerializer(), eventFileResolver);
    }

    /**
     * Initialize the FileSystemEventStore using the given <code>serializer</code>. The serializer must be capable of
     * serializing at least DomainEvents.
     *
     * @param serializer The serializer capable of serializing (at least) DomainEvents
     * @param eventFileResolver The EventFileResolver providing access to event files
     */
    public FileSystemEventStore(Serializer serializer, EventFileResolver eventFileResolver) {
        this.eventSerializer = serializer;
        this.eventFileResolver = eventFileResolver;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation writes events to an event log on the file system. It uses a directory per type of aggregate,
     * containing 1 file per aggregate.
     */
    @Override
    public void appendEvents(String type, DomainEventStream eventsToStore) {
        if (!eventsToStore.hasNext()) {
            return;
        }

        OutputStream out = null;
        try {
            DomainEventMessage next = eventsToStore.next();
            out = eventFileResolver.openEventFileForWriting(type, next.getAggregateIdentifier());
            EventMessageWriter eventMessageWriter = new EventMessageWriter(new DataOutputStream(out), eventSerializer);
            do {
                eventMessageWriter.writeEventMessage(next);
                if (eventsToStore.hasNext()) {
                    next = eventsToStore.next();
                } else {
                    next = null;
                }
            } while (next != null);
        } catch (IOException e) {
            throw new EventStoreException("Unable to store given entity due to an IOException", e);
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    @Override
    public DomainEventStream readEvents(String type, Object aggregateIdentifier) {
        try {
            if (!eventFileResolver.eventFileExists(type, aggregateIdentifier)) {
                throw new EventStreamNotFoundException(type, aggregateIdentifier);
            }

            InputStream eventFileInputStream = eventFileResolver.openEventFileForReading(type, aggregateIdentifier);
            DomainEventMessage snapshotEvent = readSnapshotEvent(type, aggregateIdentifier, eventFileInputStream);

            InputStream is = eventFileInputStream;
            if (snapshotEvent != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                EventMessageWriter snapshotEventMessageWriter =
                        new EventMessageWriter(new DataOutputStream(baos), eventSerializer);
                snapshotEventMessageWriter.writeEventMessage(snapshotEvent);
                is = new SequenceInputStream(new ByteArrayInputStream(baos.toByteArray()), eventFileInputStream);
            }
            return new BufferedReaderDomainEventStream(is, eventSerializer);
        } catch (IOException e) {
            throw new EventStoreException(
                    String.format("An error occurred while trying to open the event file "
                                          + "for aggregate type [%s] with identifier [%s]",
                                  type, aggregateIdentifier), e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws EventStoreException when an error occurs while reading or writing to the event logs.
     */
    @Override
    public void appendSnapshotEvent(String type, DomainEventMessage snapshotEvent) throws EventStoreException {
        InputStream eventFile = null;
        try {
            eventFile = eventFileResolver.openEventFileForReading(type, snapshotEvent.getAggregateIdentifier());
            OutputStream snapshotEventFile =
                    eventFileResolver.openSnapshotFileForWriting(type, snapshotEvent.getAggregateIdentifier());
            FileSystemSnapshotEventWriter snapshotEventWriter =
                    new FileSystemSnapshotEventWriter(eventFile, snapshotEventFile, eventSerializer);

            snapshotEventWriter.writeSnapshotEvent(snapshotEvent);
        } catch (IOException e) {
            throw new EventStoreException("Error writing a snapshot event due to an IO exception", e);
        } finally {
            IOUtils.closeQuietly(eventFile);
        }
    }

    private DomainEventMessage readSnapshotEvent(String type, Object identifier, InputStream eventFileInputStream)
            throws IOException {
        DomainEventMessage snapshotEvent = null;
        if (eventFileResolver.snapshotFileExists(type, identifier)) {
            InputStream snapshotEventFile = eventFileResolver.openSnapshotFileForReading(type, identifier);
            FileSystemSnapshotEventReader fileSystemSnapshotEventReader =
                    new FileSystemSnapshotEventReader(eventFileInputStream, snapshotEventFile, eventSerializer);
            snapshotEvent = fileSystemSnapshotEventReader.readSnapshotEvent(type, identifier);
        }
        return snapshotEvent;
    }

    /**
     * DomainEventStream implementation that reads DomainEvents from an {@link InputStream}.
     */
    private static class BufferedReaderDomainEventStream implements DomainEventStream {

        private DomainEventMessage next;
        private final EventMessageReader eventMessageReader;

        /**
         * Initialize a BufferedReaderDomainEventStream using the given <code>inputStream</code> and
         * <code>serializer</code>. The <code>inputStream</code> must provide a serialized DomainEvent, prefixed with a
         * UTF-8 encoded number indicating the number of bytes to read and a number representing the sequence number of
         * the event. In between each number and the serialized DomainEvent, there must be at least a single whitespace
         * character.
         * <p/>
         * Example:<br/><code>1234 The serialized domain event using 1234 bytes...</code>
         * <p/>
         * The reader will be closed when the last event has been read from it, or when an exception occurs while
         * reading or deserializing an event.
         *
         * @param inputStream The inputStream providing serialized DomainEvents
         * @param serializer  The serializer to deserialize the DomainEvents
         */
        public BufferedReaderDomainEventStream(InputStream inputStream, Serializer serializer) {
            this.eventMessageReader = new EventMessageReader(
                    new DataInputStream(new BufferedInputStream(inputStream)), serializer);
            this.next = doReadNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public DomainEventMessage next() {
            DomainEventMessage toReturn = next;
            next = doReadNext();
            return toReturn;
        }

        @Override
        public DomainEventMessage peek() {
            return next;
        }

        private DomainEventMessage doReadNext() {
            try {
                return (DomainEventMessage) eventMessageReader.readEventMessage();
            } catch (EOFException e) {
                // No more events available
                return null;
            } catch (IOException e) {
                throw new EventStoreException("An error occurred while reading from the underlying source", e);
            } catch (RuntimeException e) {
                throw e;
            }
        }
    }
}
