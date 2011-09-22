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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventstore.EventSerializer;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventStoreException;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.eventstore.XStreamEventSerializer;
import org.axonframework.eventstore.legacy.LegacyEventSerializerWrapper;
import org.axonframework.serializer.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;

import static org.axonframework.eventstore.fs.EventSerializationUtils.*;

/**
 * Implementation of the {@link org.axonframework.eventstore.EventStore} that serializes objects using XStream and
 * writes them to files to disk. Each aggregate is represented by a single file, where each event of that aggregate is
 * a
 * line in that file. Events are serialized to XML format, making them readable for both user and machine.
 * <p/>
 * Use {@link #setBaseDir(java.io.File)} to specify the directory where event files should be stored.
 * <p/>
 * Note that the resource supplied must point to a folder and should contain a trailing slash. See {@link
 * org.springframework.core.io.FileSystemResource#FileSystemResource(String)}.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class FileSystemEventStore implements EventStore, SnapshotEventStore {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemEventStore.class);

    private final Serializer<? super DomainEvent> eventSerializer;
    private EventFileResolver eventFileResolver;

    /**
     * Basic initialization of the event store. The actual serialization and deserialization is delegated to a {@link
     * org.axonframework.eventstore.XStreamEventSerializer}
     */
    public FileSystemEventStore() {
        this.eventSerializer = new XStreamEventSerializer();
    }

    /**
     * Customized initialization of the event store. The actual serialization and deserialization is delegated to the
     * provided <code>eventSerializer </code>.
     *
     * @param eventSerializer The serializer to serialize DomainEvents with
     * @deprecated Use {@link #FileSystemEventStore(org.axonframework.serializer.Serializer)} instead
     */
    @Deprecated
    public FileSystemEventStore(final EventSerializer eventSerializer) {
        this(new LegacyEventSerializerWrapper(eventSerializer));
    }

    /**
     * Initialize the FileSystemEventStore using the given <code>serializer</code>. The serializer must be capable of
     * serializing at least DomainEvents.
     *
     * @param serializer The serializer capable of serializing (at least) DomainEvents
     */
    public FileSystemEventStore(Serializer<? super DomainEvent> serializer) {
        this.eventSerializer = serializer;
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
            DomainEvent next = eventsToStore.next();
            out = eventFileResolver.openEventFileForWriting(type, next.getAggregateIdentifier());
            do {
                byte[] bytes = eventSerializer.serialize(next);
                String timeStamp = next.getTimestamp().toString();
                writeEventEntry(out, next.getSequenceNumber(), timeStamp, bytes);
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

    /**
     * {@inheritDoc}
     */
    @Override
    public DomainEventStream readEvents(String type, AggregateIdentifier identifier) {
        try {
            if (!eventFileResolver.eventFileExists(type, identifier)) {
                throw new EventStreamNotFoundException(type, identifier);
            }
            InputStream eventFileInputStream = eventFileResolver.openEventFileForReading(type, identifier);
            return readEvents(type, identifier, eventFileInputStream);
        } catch (IOException e) {
            throw new EventStoreException(
                    String.format("An error occurred while trying to open the event file "
                                          + "for aggregate type [%s] with identifier [%s]",
                                  type,
                                  identifier.asString()), e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws EventStoreException when an error occurs while reading or writing to the event logs.
     */
    @Override
    public void appendSnapshotEvent(String type, DomainEvent snapshotEvent) {
        AggregateIdentifier aggregateIdentifier = snapshotEvent.getAggregateIdentifier();
        OutputStream fileOutputStream = null;
        try {

            byte[] serializedEvent = eventSerializer.serialize(snapshotEvent);

            long offset = calculateOffset(type, aggregateIdentifier, snapshotEvent.getSequenceNumber());
            long sequenceNumber = snapshotEvent.getSequenceNumber();
            String timeStamp = snapshotEvent.getTimestamp().toString();
            SnapshotEventEntry snapshotEntry = new SnapshotEventEntry(serializedEvent,
                                                                      sequenceNumber,
                                                                      timeStamp,
                                                                      offset);

            fileOutputStream = eventFileResolver.openSnapshotFileForWriting(type, aggregateIdentifier);

            EventSerializationUtils.writeSnapshotEntry(fileOutputStream, snapshotEntry);
        } catch (IOException e) {
            throw new EventStoreException("Error writing a snapshot event due to an IO exception", e);
        } finally {
            IOUtils.closeQuietly(fileOutputStream);
        }
    }

    private long calculateOffset(String type, AggregateIdentifier aggregateIdentifier, long sequenceNumber)
            throws IOException {
        CountingInputStream countingInputStream = null;
        try {
            InputStream eventInputStream = eventFileResolver.openEventFileForReading(type, aggregateIdentifier);
            countingInputStream = new CountingInputStream(new BufferedInputStream(eventInputStream));
            long lastReadSequenceNumber = -1;
            while (lastReadSequenceNumber < sequenceNumber) {
                EventEntry entry = readEventEntry(countingInputStream);
                lastReadSequenceNumber = entry.getSequenceNumber();
            }
            return countingInputStream.getByteCount();
        } finally {
            IOUtils.closeQuietly(countingInputStream);
        }
    }

    private DomainEventStream readEvents(String type, AggregateIdentifier identifier, InputStream eventFileInputStream)
            throws IOException {
        SnapshotEventEntry snapshotEntry = readSnapshotEvent(type, identifier, eventFileInputStream);
        InputStream is = eventFileInputStream;
        if (snapshotEntry != null) {
            String timeStamp = snapshotEntry.getTimeStamp();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writeEventEntry(baos, snapshotEntry.getSequenceNumber(), timeStamp, snapshotEntry.getBytes());
            is = new SequenceInputStream(new ByteArrayInputStream(baos.toByteArray()), eventFileInputStream);
        }
        return new BufferedReaderDomainEventStream(is, eventSerializer);
    }

    private SnapshotEventEntry readSnapshotEvent(String type, AggregateIdentifier identifier,
                                                 InputStream eventFileInputStream)
            throws IOException {
        SnapshotEventEntry snapshotEvent = null;
        if (eventFileResolver.snapshotFileExists(type, identifier)) {
            InputStream snapshotFileInputStream = eventFileResolver.openSnapshotFileForReading(type, identifier);
            try {
                snapshotEvent = readLastSnapshotEntry(snapshotFileInputStream);
                long actuallySkipped = eventFileInputStream.skip(snapshotEvent.getOffset());
                if (actuallySkipped != snapshotEvent.getOffset()) {
                    logger.warn(
                            "The skip operation did not actually skip the expected amount of bytes. "
                                    + "The event log of aggregate of type {} and identifier {} might be corrupt.",
                            type,
                            identifier.asString());
                }
            } finally {
                IOUtils.closeQuietly(snapshotFileInputStream);
            }
        }
        return snapshotEvent;
    }

    /**
     * Sets the base directory where the event store will store all events.
     *
     * @param baseDir the location to store event files
     */
    public void setBaseDir(File baseDir) {
        eventFileResolver = new SimpleEventFileResolver(baseDir);
    }

    /**
     * Sets the event file resolver to use. This setter is an alternative to the {@link #setBaseDir(java.io.File)} one.
     *
     * @param eventFileResolver The EventFileResolver providing access to event files
     */
    public void setEventFileResolver(EventFileResolver eventFileResolver) {
        this.eventFileResolver = eventFileResolver;
    }

    /**
     * DomainEventStream implementation that reads DomainEvents from an inputItream. Entries in the input stream must
     * be formatted as described by {@link EventSerializationUtils}
     */
    private static class BufferedReaderDomainEventStream implements DomainEventStream {

        private DomainEvent next;
        private final InputStream inputStream;
        private final Serializer<? super DomainEvent> serializer;

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
        public BufferedReaderDomainEventStream(InputStream inputStream, Serializer<? super DomainEvent> serializer) {
            this.inputStream = new BufferedInputStream(inputStream);
            this.serializer = serializer;
            this.next = doReadNext();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasNext() {
            return next != null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DomainEvent next() {
            DomainEvent toReturn = next;
            next = doReadNext();
            return toReturn;
        }

        @Override
        public DomainEvent peek() {
            return next;
        }

        private DomainEvent doReadNext() {
            try {
                EventEntry serializedEvent = readEventEntry(inputStream);
                if (serializedEvent == null) {
                    IOUtils.closeQuietly(inputStream);
                    return null;
                }
                return serializedEvent.deserialize(serializer);
            } catch (IOException e) {
                IOUtils.closeQuietly(inputStream);
                throw new EventStoreException("An error occurred while reading from the underlying source", e);
            } catch (RuntimeException e) {
                IOUtils.closeQuietly(inputStream);
                throw e;
            }
        }
    }
}
