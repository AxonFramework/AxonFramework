/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.eventstore.jdbc;

import org.axonframework.common.Assert;
import org.axonframework.common.io.IOUtils;
import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.DataSourceConnectionProvider;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.PartialStreamSupport;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.eventstore.jdbc.criteria.JdbcCriteria;
import org.axonframework.eventstore.jdbc.criteria.JdbcCriteriaBuilder;
import org.axonframework.eventstore.jdbc.criteria.ParameterRegistry;
import org.axonframework.eventstore.management.Criteria;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.eventstore.management.EventStoreManagement;
import org.axonframework.repository.ConcurrencyException;
import org.axonframework.serializer.MessageSerializer;
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.axonframework.upcasting.SimpleUpcasterChain;
import org.axonframework.upcasting.UpcasterAware;
import org.axonframework.upcasting.UpcasterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import static org.axonframework.common.IdentifierValidator.validateIdentifier;
import static org.axonframework.upcasting.UpcastUtils.upcastAndDeserialize;

/**
 * An EventStore implementation that uses JPA to store DomainEvents in a database. The actual DomainEvent is stored as
 * a
 * serialized blob of bytes. Other columns are used to store meta-data that allow quick finding of DomainEvents for a
 * specific aggregate in the correct order.
 * <p/>
 * This EventStore supports snapshots pruning, which can enabled by configuring a {@link #setMaxSnapshotsArchived(int)
 * maximum number of snapshots to archive}. By default snapshot pruning is configured to archive only {@value
 * #DEFAULT_MAX_SNAPSHOTS_ARCHIVED} snapshot per aggregate.
 * <p/>
 * The serializer used to serialize the events is configurable. By default, the {@link org.axonframework.serializer.xml.XStreamSerializer} is used.
 *
 * @author Allard Buijze
 * @author Kristian Rosenvold
 * @since 2.1
 */
public class JdbcEventStore implements SnapshotEventStore, EventStoreManagement, UpcasterAware, PartialStreamSupport {

    private static final Logger logger = LoggerFactory.getLogger(JdbcEventStore.class);

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_MAX_SNAPSHOTS_ARCHIVED = 1;

    private final MessageSerializer serializer;
    private final EventEntryStore eventEntryStore;
    private final JdbcCriteriaBuilder criteriaBuilder = new JdbcCriteriaBuilder();

    private int batchSize = DEFAULT_BATCH_SIZE;
    private UpcasterChain upcasterChain = SimpleUpcasterChain.EMPTY;
    private int maxSnapshotsArchived = DEFAULT_MAX_SNAPSHOTS_ARCHIVED;
    private PersistenceExceptionResolver persistenceExceptionResolver;

    public JdbcEventStore(EventEntryStore eventEntryStore, Serializer serializer) {
        Assert.notNull(serializer, "serializer may not be null");
        Assert.notNull(eventEntryStore, "eventEntryStore may not be null");
        this.persistenceExceptionResolver = new JdbcSQLErrorCodesResolver();
        this.serializer = new MessageSerializer(serializer);
        this.eventEntryStore = eventEntryStore;
    }

    /**
     * Initialize a JdbcEventStore using the given <code>eventEntryStore</code> and an {@link
     * org.axonframework.serializer.xml.XStreamSerializer}, which serializes events as XML.
     *
     * @param eventEntryStore       The instance providing persistence logic for Domain Event entries
     */
    public JdbcEventStore(EventEntryStore eventEntryStore) {
        this(eventEntryStore, new XStreamSerializer());
    }

    /**
     * Initialize a JdbcEventStore using the given <code>eventEntryStore</code> and an {@link
     * org.axonframework.serializer.xml.XStreamSerializer}, which serializes events as XML.
     *
     */
    public JdbcEventStore(ConnectionProvider connectionProvider) {
        this(new DefaultEventEntryStore(connectionProvider), new XStreamSerializer());
    }

    /**
     * Initialize a JdbcEventStore using the given <code>eventEntryStore</code> and an {@link
     * org.axonframework.serializer.xml.XStreamSerializer}, which serializes events as XML.
     *
     */
    public JdbcEventStore(DataSource dataSource) {
        this(new DefaultEventEntryStore(new DataSourceConnectionProvider(dataSource)), new XStreamSerializer());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void appendEvents(String type, DomainEventStream events) {
        DomainEventMessage event = null;
        try {
            while (events.hasNext()) {
                event = events.next();
                validateIdentifier(event.getAggregateIdentifier().getClass());
                SerializedObject<byte[]> serializedPayload = serializer.serializePayload(event, byte[].class);
                SerializedObject<byte[]> serializedMetaData = serializer.serializeMetaData(event, byte[].class);
                eventEntryStore.persistEvent(type, event, serializedPayload, serializedMetaData);
            }
        } catch (RuntimeException exception) {
            if (persistenceExceptionResolver != null
                    && persistenceExceptionResolver.isDuplicateKeyViolation(exception)) {
                //noinspection ConstantConditions
                throw new ConcurrencyException(
                        String.format("Concurrent modification detected for Aggregate identifier [%s], sequence: [%s]",
                                      event.getAggregateIdentifier(),
                                      event.getSequenceNumber()),
                        exception);
            }
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public DomainEventStream readEvents(String type, Object identifier) {
        long snapshotSequenceNumber = -1;
        SerializedDomainEventData lastSnapshotEvent = eventEntryStore.loadLastSnapshotEvent(type, identifier);
        DomainEventMessage snapshotEvent = null;
        if (lastSnapshotEvent != null) {
            try {
                snapshotEvent = new GenericDomainEventMessage<Object>(
                        identifier,
                        lastSnapshotEvent.getSequenceNumber(),
                        serializer.deserialize(lastSnapshotEvent.getPayload()),
                        (Map<String, Object>) serializer.deserialize(lastSnapshotEvent.getMetaData()));
                snapshotSequenceNumber = snapshotEvent.getSequenceNumber();
            } catch (RuntimeException ex) {
                logger.warn("Error while reading snapshot event entry. "
                                    + "Reconstructing aggregate on entire event stream. Caused by: {} {}",
                            ex.getClass().getName(),
                            ex.getMessage());
            } catch (LinkageError error) {
                logger.warn("Error while reading snapshot event entry. "
                                    + "Reconstructing aggregate on entire event stream. Caused by: {} {}",
                            error.getClass().getName(),
                            error.getMessage());
            }
        }

        Iterator<? extends SerializedDomainEventData> entries =
                eventEntryStore.fetchAggregateStream(type, identifier, snapshotSequenceNumber + 1, batchSize);
        if (snapshotEvent == null && !entries.hasNext()) {
            throw new EventStreamNotFoundException(type, identifier);
        }
        return new IteratorDomainEventStream(snapshotEvent, entries, identifier, false);
    }

    @Override
    public DomainEventStream readEvents(String type, Object identifier, long firstSequenceNumber) {
        return readEvents(type, identifier, firstSequenceNumber, Long.MAX_VALUE);
    }

    @Override
    public DomainEventStream readEvents(String type, Object identifier, long firstSequenceNumber,
                                        long lastSequenceNumber) {
        int minimalBatchSize = (int) Math.min(batchSize, (lastSequenceNumber - firstSequenceNumber) + 2);
        Iterator<? extends SerializedDomainEventData> entries = eventEntryStore.fetchAggregateStream(type,
                                                                                                     identifier,
                                                                                                     firstSequenceNumber,
                                                                                                     minimalBatchSize);
        if (!entries.hasNext()) {
            throw new EventStreamNotFoundException(type, identifier);
        }
        return new IteratorDomainEventStream(null, entries, identifier, lastSequenceNumber, false);
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Upon appending a snapshot, this particular EventStore implementation also prunes snapshots which are considered
     * redundant because they fall outside of the range of maximum snapshots to archive.
     */
    @Override
    public void appendSnapshotEvent(String type, DomainEventMessage snapshotEvent) {
        // Persist snapshot before pruning redundant archived ones, in order to prevent snapshot misses when reloading
        // an aggregate, which may occur when a READ_UNCOMMITTED transaction isolation level is used.
        SerializedObject<byte[]> serializedPayload = serializer.serializePayload(snapshotEvent, byte[].class);
        SerializedObject<byte[]> serializedMetaData = serializer.serializeMetaData(snapshotEvent, byte[].class);
        eventEntryStore.persistSnapshot(type, snapshotEvent, serializedPayload, serializedMetaData);

        if (maxSnapshotsArchived > 0) {
            eventEntryStore.pruneSnapshots(type, snapshotEvent, maxSnapshotsArchived);
        }
    }

    @Override
    public void visitEvents(EventVisitor visitor) {
        doVisitEvents(visitor, null, Collections.<Object>emptyList());
    }

    @Override
    public void visitEvents(Criteria criteria, EventVisitor visitor) {
        StringBuilder sb = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        ((JdbcCriteria) criteria).parse("", sb, parameters);
        doVisitEvents(visitor, sb.toString(), parameters.getParameters());
    }

    @Override
    public CriteriaBuilder newCriteriaBuilder() {
        return criteriaBuilder;
    }

    private void doVisitEvents(EventVisitor visitor, String whereClause, List<Object> parameters) {
        Iterator<? extends SerializedDomainEventData> batch = eventEntryStore.fetchFiltered(whereClause,
                                                                                            parameters,
                                                                                            batchSize
                                                                                            );
        DomainEventStream eventStream = new IteratorDomainEventStream(null, batch, null, true);
		try {
			while (eventStream.hasNext()) {
				visitor.doWithEvent(eventStream.next());
			}
		} finally {
			IOUtils.closeQuietlyIfCloseable(eventStream);
		}
    }

    /**
     * Sets the persistenceExceptionResolver that will help detect concurrency exceptions from the backing database.
     *
     * @param persistenceExceptionResolver the persistenceExceptionResolver that will help detect concurrency
     *                                     exceptions
     */
    public void setPersistenceExceptionResolver(PersistenceExceptionResolver persistenceExceptionResolver) {
        this.persistenceExceptionResolver = persistenceExceptionResolver;
    }

    /**
     * Sets the number of events that should be read at each database access. When more than this number of events must
     * be read to rebuild an aggregate's state, the events are read in batches of this size. Defaults to 100.
     * <p/>
     * Tip: if you use a snapshotter, make sure to choose snapshot trigger and batch size such that a single batch will
     * generally retrieve all events required to rebuild an aggregate's state.
     *
     * @param batchSize the number of events to read on each database access. Default to 100.
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    @Override
    public void setUpcasterChain(UpcasterChain upcasterChain) {
        this.upcasterChain = upcasterChain;
    }

    /**
     * Sets the maximum number of snapshots to archive for an aggregate. The EventStore will keep at most this number
     * of
     * snapshots per aggregate.
     * <p/>
     * Defaults to {@value #DEFAULT_MAX_SNAPSHOTS_ARCHIVED}.
     *
     * @param maxSnapshotsArchived The maximum number of snapshots to archive for an aggregate. A value less than 1
     *                             disables pruning of snapshots.
     */
    public void setMaxSnapshotsArchived(int maxSnapshotsArchived) {
        this.maxSnapshotsArchived = maxSnapshotsArchived;
    }

    private final class IteratorDomainEventStream implements DomainEventStream, Closeable {

        private Iterator<DomainEventMessage> currentBatch;
        private DomainEventMessage next;
        private final Iterator<? extends SerializedDomainEventData> iterator;
        private final Object aggregateIdentifier;
        private final long lastSequenceNumber;
        private final boolean skipUnknownTypes;

        public IteratorDomainEventStream(DomainEventMessage snapshotEvent,
                                             Iterator<? extends SerializedDomainEventData> iterator,
                                             Object aggregateIdentifier, boolean skipUnknownTypes) {
            this(snapshotEvent, iterator, aggregateIdentifier, Long.MAX_VALUE, skipUnknownTypes);
        }

        public IteratorDomainEventStream(DomainEventMessage snapshotEvent,
                                             Iterator<? extends SerializedDomainEventData> iterator,
                                             Object aggregateIdentifier, long lastSequenceNumber,
                                             boolean skipUnknownTypes) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.lastSequenceNumber = lastSequenceNumber;
            this.skipUnknownTypes = skipUnknownTypes;
            if (snapshotEvent != null) {
                currentBatch = Collections.singletonList(snapshotEvent).iterator();
            } else {
                currentBatch = Collections.<DomainEventMessage>emptyList().iterator();
            }
            this.iterator = iterator;
            initializeNextItem();
        }



        @Override
        public boolean hasNext() {
            return next != null && next.getSequenceNumber() <= lastSequenceNumber;
        }

        @Override
        public DomainEventMessage next() {
            DomainEventMessage current = next;
            initializeNextItem();
            return current;
        }

        private void initializeNextItem() {
            while (!currentBatch.hasNext() && iterator.hasNext()) {
                final SerializedDomainEventData entry = iterator.next();
                currentBatch = upcastAndDeserialize(entry, aggregateIdentifier, serializer, upcasterChain,
                                                    skipUnknownTypes)
                        .iterator();
            }
            next = currentBatch.hasNext() ? currentBatch.next() : null;
        }

        @Override
        public DomainEventMessage peek() {
            return next;
        }

		@Override
		public void close() throws IOException {
			IOUtils.closeIfCloseable(iterator);
		}
	}
}
