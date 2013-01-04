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

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventstore.EventStoreException;
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedDomainEventMessage;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.upcasting.SerializedDomainEventUpcastingContext;
import org.axonframework.upcasting.UpcastSerializedDomainEventData;
import org.axonframework.upcasting.UpcasterChain;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * DomainEventStream implementation that reads DomainEvents from the filesystem using an {@link java.io.InputStream}.
 * Directly upcasts the events after reading using an {@link UpcasterChain}.
 *
 * @author Allard Buijze
 * @author Frank Versnel
 * @since 0.5
 */
public class FileSystemBufferedReaderDomainEventStream implements DomainEventStream {

    private Queue<DomainEventMessage> next;
    private final FileSystemEventMessageReader eventMessageReader;
    private final UpcasterChain upcasterChain;
    private final Serializer serializer;

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
     * @param inputStream   The inputStream providing serialized DomainEvents
     * @param serializer    The serializer to deserialize the DomainEvents
     * @param upcasterChain used to upcast events directly after being read
     */
    public FileSystemBufferedReaderDomainEventStream(InputStream inputStream,
                                                     Serializer serializer,
                                                     UpcasterChain upcasterChain) {
        this.eventMessageReader = new FileSystemEventMessageReader(
                new DataInputStream(new BufferedInputStream(inputStream)));
        this.upcasterChain = upcasterChain;
        this.serializer = serializer;
        this.next = new LinkedList<DomainEventMessage>();
        next.addAll(doReadNext());
    }

    @Override
    public boolean hasNext() {
        // Load new events if no more events are left in the queue.
        if (next.isEmpty()) {
            next.addAll(doReadNext());
        }

        return !next.isEmpty();
    }

    @Override
    public DomainEventMessage next() {
        return next.poll();
    }

    @Override
    public DomainEventMessage peek() {
        return next.peek();
    }

    private List<DomainEventMessage> doReadNext() {
        try {
            List<DomainEventMessage> upcastEvents;
            do {
                SerializedDomainEventData eventFromFile = eventMessageReader.readEventMessage();
                upcastEvents = upcast(eventFromFile);
            } while (upcastEvents.isEmpty());
            return upcastEvents;
        } catch (EOFException e) {
            // No more events available
            return Collections.emptyList();
        } catch (IOException e) {
            throw new EventStoreException("An error occurred while reading from the underlying source", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<DomainEventMessage> upcast(final SerializedDomainEventData entry) {
        final SerializedDomainEventUpcastingContext context = new SerializedDomainEventUpcastingContext(entry,
                                                                                                        serializer);
        List<SerializedObject> objects = upcasterChain.upcast(
                entry.getPayload(), context);
        List<DomainEventMessage> events = new ArrayList<DomainEventMessage>(objects.size());
        for (SerializedObject object : objects) {
            DomainEventMessage<Object> message = new SerializedDomainEventMessage<Object>(
                    new UpcastSerializedDomainEventData(entry, entry.getAggregateIdentifier(), object), serializer);

            // prevents duplicate deserialization of meta data when it has already been access during upcasting
            if (context.getSerializedMetaData().isDeserialized()) {
                message = message.withMetaData(context.getSerializedMetaData().getObject());
            }
            events.add(message);
        }
        return events;
    }
}