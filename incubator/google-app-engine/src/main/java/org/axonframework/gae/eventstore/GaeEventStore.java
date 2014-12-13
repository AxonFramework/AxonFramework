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

package org.axonframework.gae.eventstore;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Transaction;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.core.util.ClassLoaderReference;
import com.thoughtworks.xstream.core.util.CompositeClassLoader;
import com.thoughtworks.xstream.io.xml.XppDriver;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.gae.serializer.GaeXStream;
import org.axonframework.serializer.MessageSerializer;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.axonframework.upcasting.SimpleUpcasterChain;
import org.axonframework.upcasting.UpcasterAware;
import org.axonframework.upcasting.UpcasterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * EventStore implementation that uses Google App Engine's DatastoreService to store Event Streams.
 *
 * @author Jettro Coenradie
 * @since 1.0
 */
public class GaeEventStore implements SnapshotEventStore, UpcasterAware {

    private static final Logger logger = LoggerFactory.getLogger(GaeEventStore.class);

    private final MessageSerializer eventSerializer;
    private final DatastoreService datastoreService;
    private UpcasterChain upcasterChain = SimpleUpcasterChain.EMPTY;

    /**
     * Constructs an instance using a GAE compatible instance of the XStreamSerializer.
     */
    public GaeEventStore() {
        this(new XStreamSerializer(new GaeXStream(new PureJavaReflectionProvider(), new XppDriver(),
                                                  new ClassLoaderReference(new CompositeClassLoader()))));
    }

    /**
     * Constructs and instance using the given <code>eventSerializer</code>.
     *
     * @param eventSerializer The serializer to serialize payload and metadata of EventMessages with.
     */
    public GaeEventStore(Serializer eventSerializer) {
        this.eventSerializer = new MessageSerializer(eventSerializer);
        this.datastoreService = DatastoreServiceFactory.getDatastoreService();
    }

    @Override
    public void appendEvents(String type, DomainEventStream events) {
        while (events.hasNext()) {
            DomainEventMessage event = events.next();
            doStoreEvent(type, event);
        }
    }

    @Override
    public DomainEventStream readEvents(String type, Object identifier) {
        long snapshotSequenceNumber = -1;
        EventEntry lastSnapshotEvent = loadLastSnapshotEvent(type, identifier);
        if (lastSnapshotEvent != null) {
            snapshotSequenceNumber = lastSnapshotEvent.getSequenceNumber();
        }

        List<DomainEventMessage> events = readEventSegmentInternal(type, identifier, snapshotSequenceNumber + 1,
                                                                   Long.MAX_VALUE);
        if (lastSnapshotEvent != null) {
            events.addAll(0, lastSnapshotEvent.getDomainEvent(identifier, eventSerializer, upcasterChain, false));
        }

        if (events.isEmpty()) {
            throw new EventStreamNotFoundException(type, identifier);
        }

        return new SimpleDomainEventStream(events);
    }

    @Override
    public DomainEventStream readEvents(String type, Object identifier, long firstSequenceNumber,
                                        long lastSequenceNumber) {
        List<DomainEventMessage> events = readEventSegmentInternal(type, identifier,
                                                                   firstSequenceNumber, lastSequenceNumber);
        if (events.isEmpty()) {
            throw new EventStreamNotFoundException(type, identifier);
        }

        return new SimpleDomainEventStream(events);
    }

    @Override
    public void appendSnapshotEvent(String type, DomainEventMessage snapshotEvent) {
        String snapshotType = "snapshot_" + type;
        doStoreEvent(snapshotType, snapshotEvent);
    }

    private void doStoreEvent(String type, DomainEventMessage event) {
        EventEntry entry = new EventEntry(type, event, eventSerializer);
        Transaction transaction = datastoreService.beginTransaction();
        try {
            datastoreService.put(transaction, entry.asEntity());
            transaction.commit();
        } finally {
            if (transaction.isActive()) {
                logger.info("Transaction to commit new events is rolled back because");
                transaction.rollback();
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("event of type {} appended", type);
                }
            }
        }
    }

    private List<DomainEventMessage> readEventSegmentInternal(String type, Object identifier,
                                                              long firstSequenceNumber, long lastSequenceNumber) {
        Query query = EventEntry.forAggregate(type, identifier.toString(), firstSequenceNumber, lastSequenceNumber);
        PreparedQuery preparedQuery = datastoreService.prepare(query);
        List<Entity> entities = preparedQuery.asList(FetchOptions.Builder.withDefaults());

        List<DomainEventMessage> events = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            events.addAll(new EventEntry(entity).getDomainEvent(identifier, eventSerializer, upcasterChain, false));
        }
        return events;
    }

    private EventEntry loadLastSnapshotEvent(String type, Object identifier) {
        Query query = EventEntry.forLastSnapshot("snapshot_" + type, identifier.toString());
        PreparedQuery preparedQuery = datastoreService.prepare(query);
        Iterator<Entity> entityIterator = preparedQuery.asIterable().iterator();
        if (entityIterator.hasNext()) {
            Entity lastSnapshot = entityIterator.next();
            return new EventEntry(lastSnapshot);
        }
        return null;
    }

    @Override
    public void setUpcasterChain(UpcasterChain upcasterChain) {
        this.upcasterChain = upcasterChain;
    }
}
