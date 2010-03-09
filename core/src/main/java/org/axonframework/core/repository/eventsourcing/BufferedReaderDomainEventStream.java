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
import org.axonframework.core.DomainEvent;
import org.axonframework.core.DomainEventStream;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * DomainEventStream implementation that reads DomainEvents from a BufferedReader. It expects each DomainEvent to be
 * separated using a line terminator.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class BufferedReaderDomainEventStream implements DomainEventStream {

    private DomainEvent next;
    private BufferedReader reader;
    private EventSerializer serializer;

    /**
     * Initialize a BufferedReaderDomainEventStream using the given <code>reader</code> and <code>serializer</code>. The
     * <code>reader</code> must provide a serialized DomainEvent on every line. Thus, <code>reader.readLine()</code>
     * must return exactly one serialized DomainEvent, except when the end of file has been reached, in which case it
     * should return null.
     * <p/>
     * The reader will be closed when the last event has been read from it, or when an exception occurs while reading or
     * deserializing an event.
     *
     * @param reader     The reader providing serialized DomainEvents
     * @param serializer The serializer to deserialize the DomainEvents
     */
    public BufferedReaderDomainEventStream(BufferedReader reader, EventSerializer serializer) {
        this.reader = reader;
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
            String line = reader.readLine();
            if (line == null) {
                IOUtils.closeQuietly(reader);
                return null;
            }
            return serializer.deserialize(line.getBytes("UTF-8"));
        } catch (IOException e) {
            IOUtils.closeQuietly(reader);
            throw new EventStoreException("An error occurred while reading from the underlying source", e);
        } catch (RuntimeException e) {
            IOUtils.closeQuietly(reader);
            throw e;
        }
    }
}
