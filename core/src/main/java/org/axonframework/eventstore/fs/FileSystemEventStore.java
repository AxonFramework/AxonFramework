/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.common.Assert;
import org.axonframework.common.io.IOUtils;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.DomainEventStream;
import org.axonframework.eventstore.EventStoreException;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.repository.ConflictingModificationException;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.axonframework.upcasting.SimpleUpcasterChain;
import org.axonframework.upcasting.UpcasterAware;
import org.axonframework.upcasting.UpcasterChain;

import java.io.*;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Implementation of the {@link org.axonframework.eventstore.EventStore} that serializes objects (by default using
 * XStream) and
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
public class FileSystemEventStore implements SnapshotEventStore, UpcasterAware {

    private final Serializer eventSerializer;
    private final EventFileResolver eventFileResolver;
    private UpcasterChain upcasterChain = SimpleUpcasterChain.EMPTY;

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
     * serializing the payload and meta data of Event Messages.
     * <p/>
     * <em>Note: the SerializedType of Message Meta Data is not stored. Upon retrieval, it is set to the default value
     * (name = "org.axonframework.messaging.metadata.MetaData", revision = null). See {@link org.axonframework.serializer.SerializedMetaData#isSerializedMetaData(org.axonframework.serializer.SerializedObject)}</em>
     *
     * @param serializer        The serializer capable of serializing (at least) DomainEvents
     * @param eventFileResolver The EventFileResolver providing access to event files
     */
    public FileSystemEventStore(Serializer serializer, EventFileResolver eventFileResolver) {
        Assert.notNull(serializer, "serializer may not be null");
        Assert.notNull(eventFileResolver, "eventFileResolver may not be null");
        this.eventSerializer = serializer;
        this.eventFileResolver = eventFileResolver;
    }

    @Override
    public void appendEvents(List<DomainEventMessage<?>> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        OutputStream out = null;
        try {
            DomainEventMessage first = events.get(0);
            if (first.getSequenceNumber() == 0
                    && eventFileResolver.eventFileExists(first.getAggregateIdentifier())) {
                throw new ConflictingModificationException("Could not create event stream for aggregate, such stream "
                                                                   + "already exists, id="
                                                                   + first.getAggregateIdentifier());
            }
            out = eventFileResolver.openEventFileForWriting(first.getAggregateIdentifier());
            FileSystemEventMessageWriter eventMessageWriter =
                    new FileSystemEventMessageWriter(new DataOutputStream(out), eventSerializer);
            for (DomainEventMessage<?> next : events) {
                eventMessageWriter.writeEventMessage(next);
            }
        } catch (IOException e) {
            throw new EventStoreException("Unable to store given entity due to an IOException", e);
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    @Override
    public DomainEventStream readEvents(String identifier, long firstSequenceNumber, long lastSequenceNumber) {
        try {
            InputStream eventFileInputStream = eventFileResolver.openEventFileForReading(identifier);
            final FileSystemBufferedReaderDomainEventStream fullStream = new FileSystemBufferedReaderDomainEventStream(
                    eventFileInputStream,
                    eventSerializer,
                    upcasterChain);
            return new DomainEventStream() {
                @Override
                public boolean hasNext() {
                    while (fullStream.hasNext() && fullStream.peek().getSequenceNumber() < firstSequenceNumber) {
                        fullStream.next();
                    }
                    return fullStream.hasNext() && fullStream.peek().getSequenceNumber() <= lastSequenceNumber;
                }

                @Override
                public DomainEventMessage next() {
                    while (fullStream.hasNext() && fullStream.peek().getSequenceNumber() < firstSequenceNumber) {
                        fullStream.next();
                    }
                    final DomainEventMessage next = fullStream.next();
                    if (next.getSequenceNumber() > lastSequenceNumber) {
                        throw new NoSuchElementException("Attempt to read beyond end of stream");
                    }
                    return next;
                }

                @Override
                public DomainEventMessage peek() {
                    final DomainEventMessage peek = fullStream.peek();
                    if (!hasNext()) {
                        throw new NoSuchElementException("Attempt to read beyond enf of stream");
                    }
                    return peek;
                }
            };
        } catch (IOException e) {
            throw new EventStoreException(
                    String.format("An error occurred while trying to open the event file "
                                          + "for aggregate with identifier [%s]",
                                  identifier), e);
        }
    }

    @Override
    public DomainEventStream readEvents(String aggregateIdentifier) {
        try {
            if (!eventFileResolver.eventFileExists(aggregateIdentifier)) {
                throw new EventStreamNotFoundException(aggregateIdentifier);
            }

            InputStream eventFileInputStream = eventFileResolver.openEventFileForReading(aggregateIdentifier);
            DomainEventMessage snapshotEvent;
            try {
                snapshotEvent = readSnapshotEvent(aggregateIdentifier, eventFileInputStream);
                // trigger deserialization
                snapshotEvent.getPayload();
            } catch (Exception e) {
                // error while reading snapshot. We ignore it
                snapshotEvent = null;
                // we need to reset the stream, as it points to the event after the snapshot
                IOUtils.closeQuietly(eventFileInputStream);
                eventFileInputStream = eventFileResolver.openEventFileForReading(aggregateIdentifier);
            }

            InputStream is = eventFileInputStream;
            if (snapshotEvent != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                FileSystemEventMessageWriter snapshotEventMessageWriter =
                        new FileSystemEventMessageWriter(new DataOutputStream(baos), eventSerializer);
                snapshotEventMessageWriter.writeEventMessage(snapshotEvent);
                is = new SequenceInputStream(new ByteArrayInputStream(baos.toByteArray()), eventFileInputStream);
            }

            return new FileSystemBufferedReaderDomainEventStream(is, eventSerializer, upcasterChain);
        } catch (IOException e) {
            throw new EventStoreException(
                    String.format("An error occurred while trying to open the event file "
                                          + "for aggregate with identifier [%s]",
                                  aggregateIdentifier), e);
        }
    }


    /**
     * {@inheritDoc}
     *
     * @throws EventStoreException when an error occurs while reading or writing to the event logs.
     */
    @Override
    public void appendSnapshotEvent(DomainEventMessage snapshotEvent) throws EventStoreException {
        InputStream eventFile = null;
        try {
            eventFile = eventFileResolver.openEventFileForReading(snapshotEvent.getAggregateIdentifier());
            OutputStream snapshotEventFile =
                    eventFileResolver.openSnapshotFileForWriting(snapshotEvent.getAggregateIdentifier());
            FileSystemSnapshotEventWriter snapshotEventWriter =
                    new FileSystemSnapshotEventWriter(eventFile, snapshotEventFile, eventSerializer);

            snapshotEventWriter.writeSnapshotEvent(snapshotEvent);
        } catch (IOException e) {
            throw new EventStoreException("Error writing a snapshot event due to an IO exception", e);
        } finally {
            IOUtils.closeQuietly(eventFile);
        }
    }

    private DomainEventMessage readSnapshotEvent(String identifier, InputStream eventFileInputStream)
            throws IOException {
        DomainEventMessage snapshotEvent = null;
        if (eventFileResolver.snapshotFileExists(identifier)) {
            InputStream snapshotEventFile = eventFileResolver.openSnapshotFileForReading(identifier);
            FileSystemSnapshotEventReader fileSystemSnapshotEventReader =
                    new FileSystemSnapshotEventReader(eventFileInputStream, snapshotEventFile, eventSerializer);
            snapshotEvent = fileSystemSnapshotEventReader.readSnapshotEvent(identifier);
        }
        return snapshotEvent;
    }

    @Override
    public void setUpcasterChain(UpcasterChain upcasterChain) {
        this.upcasterChain = upcasterChain;
    }
}
