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

import org.apache.commons.io.IOUtils;
import org.axonframework.core.AggregateNotFoundException;
import org.axonframework.core.DomainEvent;
import org.axonframework.core.DomainEventStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.core.io.Resource;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Implementation of the {@link org.axonframework.core.repository.eventsourcing.EventStore} that serializes objects
 * using XStream and writes them to files to disk. Each aggregate is represented by a single file, where each event of
 * that aggregate is a line in that file. Events are serialized to XML format, making them readable for both user and
 * machine.
 * <p/>
 * Use {@link #setBaseDir(org.springframework.core.io.Resource)} to specify the directory where event files should be
 * stored.
 * <p/>
 * Note that the resource supplied must point to a folder and should contain a trailing slash. See {@link
 * org.springframework.core.io.FileSystemResource#FileSystemResource(String)}.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class FileSystemEventStore implements EventStore {

    private static final String CHARSET_UTF8 = "UTF-8";

    private final EventSerializer eventSerializer;
    private Resource baseDir;

    /**
     * Basic initialization of the event store. The actual serialization and deserialization is delegated to the
     * provided <code>eventSerializer </code>.
     *
     * @param eventSerializer The serializer to serialize DomainEvents with
     */
    public FileSystemEventStore(EventSerializer eventSerializer) {
        this.eventSerializer = eventSerializer;
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
            out = obtainOutputStreamForAggregate(type, next.getAggregateIdentifier());
            do {
                byte[] bytes = eventSerializer.serialize(next);
                out.write(Integer.toString(bytes.length).getBytes(CHARSET_UTF8));
                IOUtils.write(" ", out, CHARSET_UTF8);
                out.write(bytes);
                IOUtils.write("\n", out, CHARSET_UTF8);
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
     * Provides an output stream to a file using for an aggregate with the given <code>aggregateIdentifier</code> and of
     * given <code>type</code>. The caller of this method is responsible for closing the output stream when all data has
     * been written to it.
     *
     * @param type                The type of aggregate to open the stream for
     * @param aggregateIdentifier the identifier of the aggregate
     * @return an OutputStream that writes to the event log of of the given aggregate
     *
     * @throws IOException when an error occurs while opening a file
     */
    protected OutputStream obtainOutputStreamForAggregate(String type, UUID aggregateIdentifier) throws IOException {
        File eventFile = new File(getBaseDirForType(type).getFile(), aggregateIdentifier + ".events");
        return new BufferedOutputStream(new FileOutputStream(eventFile, true));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DomainEventStream readEvents(String type, UUID identifier) {
        try {
            Resource eventFile = getBaseDirForType(type).createRelative(identifier + ".events");
            if (!eventFile.exists()) {
                throw new AggregateNotFoundException(
                        String.format(
                                "Aggregate of type [%s] with identifier [%s] cannot be found.",
                                type,
                                identifier.toString()));
            }
            return new BufferedReaderDomainEventStream(eventFile.getInputStream(), eventSerializer);
        } catch (IOException e) {
            throw new EventStoreException(
                    String.format("An error occurred while trying to open the event file "
                            + "for aggregate type [%s] with identifier [%s]",
                                  type,
                                  identifier.toString()), e);
        }
    }

    private Resource getBaseDirForType(String type) {
        try {
            Resource typeSpecificDir = baseDir.createRelative("/" + type + "/");
            if (!typeSpecificDir.exists() && !typeSpecificDir.getFile().mkdirs()) {
                throw new EventStoreException(
                        "The given event store directory doesn't exist and could not be created");
            }
            return typeSpecificDir;
        } catch (IOException e) {
            throw new EventStoreException("An IO Exception occurred while reading from the file system", e);
        }
    }

    /**
     * Sets the base directory where the event store will store all events.
     *
     * @param baseDir the location to store event files
     */
    @Required
    public void setBaseDir(Resource baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * DomainEventStream implementation that reads DomainEvents from a BufferedReader. It expects each DomainEvent to be
     * separated using a line terminator.
     */
    private static class BufferedReaderDomainEventStream implements DomainEventStream {

        private static final Logger logger = LoggerFactory.getLogger(BufferedReaderDomainEventStream.class);

        private DomainEvent next;
        private final InputStream inputStream;
        private final EventSerializer serializer;

        /**
         * Initialize a BufferedReaderDomainEventStream using the given <code>inputStream</code> and
         * <code>serializer</code>. The <code>inputStream</code> must provide a serialized DomainEvent, prefixed with a
         * UTF-8 encoded number indicating the number of bytes to read. In between the number and the serialized
         * DomainEvent, there must be at least a single whitespace character.
         * <p/>
         * Example:<br/><code>1234 The serialized domain event using 1234 bytes...</code>
         * <p/>
         * The reader will be closed when the last event has been read from it, or when an exception occurs while
         * reading or deserializing an event.
         *
         * @param inputStream The inputStream providing serialized DomainEvents
         * @param serializer  The serializer to deserialize the DomainEvents
         */
        public BufferedReaderDomainEventStream(InputStream inputStream, EventSerializer serializer) {
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

        private DomainEvent doReadNext() {
            try {
                int lineSize = readLineSize();
                if (lineSize < 0) {
                    IOUtils.closeQuietly(inputStream);
                    return null;
                }
                byte[] serializedEvent = new byte[lineSize];
                int bytesRead = inputStream.read(serializedEvent);
                if (logger.isWarnEnabled() && bytesRead < serializedEvent.length) {
                    logger.warn("Failed to read the required amount of bytes from the underlying stream. "
                            + "This may result in a failure to deserialize the event");
                }
                return serializer.deserialize(serializedEvent);
            } catch (IOException e) {
                IOUtils.closeQuietly(inputStream);
                throw new EventStoreException("An error occurred while reading from the underlying source", e);
            } catch (RuntimeException e) {
                IOUtils.closeQuietly(inputStream);
                throw e;
            }
        }

        private int readLineSize() throws IOException {
            int codePoint = fistNonWhitespaceCharacter();
            if (codePoint < 0) {
                return -1;
            }
            StringBuilder sb = new StringBuilder();
            while (!Character.isWhitespace(codePoint)) {
                char nextChar = (char) codePoint;
                sb.append(nextChar);
                codePoint = inputStream.read();
            }

            return Integer.parseInt(sb.toString());
        }

        private int fistNonWhitespaceCharacter() throws IOException {
            int codePoint = inputStream.read();
            while (Character.isWhitespace(codePoint)) {
                codePoint = inputStream.read();
            }
            return codePoint;
        }
    }
}
