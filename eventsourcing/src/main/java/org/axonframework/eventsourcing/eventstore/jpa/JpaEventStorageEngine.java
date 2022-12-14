/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.DateTimeUtils;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericDomainEventEntry;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackedDomainEventData;
import org.axonframework.eventhandling.TrackedEventData;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngine;
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.sql.DataSource;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;
import static org.axonframework.common.DateTimeUtils.formatInstant;

/**
 * EventStorageEngine implementation that uses JPA to store and fetch events.
 * <p>
 * By default, the payload of events is stored as a serialized blob of bytes. Other columns are used to store meta-data
 * that allow quick finding of DomainEvents for a specific aggregate in the correct order.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class JpaEventStorageEngine extends BatchingEventStorageEngine {

    private static final Logger logger = LoggerFactory.getLogger(JpaEventStorageEngine.class);

    private static final int DEFAULT_MAX_GAP_OFFSET = 10000;
    private static final long DEFAULT_LOWEST_GLOBAL_SEQUENCE = 1;
    private static final int DEFAULT_GAP_TIMEOUT = 60000;
    private static final int DEFAULT_GAP_CLEANING_THRESHOLD = 250;

    private final EntityManagerProvider entityManagerProvider;
    private final TransactionManager transactionManager;
    private final boolean explicitFlush;
    private final int maxGapOffset;
    private final long lowestGlobalSequence;
    private int gapTimeout;
    private int gapCleaningThreshold;

    /**
     * Instantiate a {@link JpaEventStorageEngine} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the event and snapshot {@link Serializer}, the {@link EntityManagerProvider} and {@link
     * TransactionManager} are not {@code null}, and will throw an {@link AxonConfigurationException} if any of them is
     * {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link JpaEventStorageEngine} instance
     */
    protected JpaEventStorageEngine(Builder builder) {
        super(builder);
        this.entityManagerProvider = builder.entityManagerProvider;
        this.transactionManager = builder.transactionManager;
        this.explicitFlush = builder.explicitFlush;
        this.maxGapOffset = builder.maxGapOffset;
        this.lowestGlobalSequence = builder.lowestGlobalSequence;
        this.gapTimeout = builder.gapTimeout;
        this.gapCleaningThreshold = builder.gapCleaningThreshold;
    }

    /**
     * Instantiate a Builder to be able to create a {@link JpaEventStorageEngine}.
     * <p>
     * The following configurable fields have defaults:
     * <ul>
     * <li>The {@link EventUpcaster} defaults to an {@link org.axonframework.serialization.upcasting.event.NoOpEventUpcaster}.</li>
     * <li>The {@link PersistenceExceptionResolver} is defaulted to a {@link SQLErrorCodesResolver}, <b>if</b> the
     * {@link DataSource} is provided</li>
     * <li>The {@code snapshotFilter} defaults to a {@link SnapshotFilter#allowAll()} instance.</li>
     * <li>The {@code batchSize} defaults to an integer of size {@code 100}.</li>
     * <li>The {@code explicitFlush} defaults to {@code true}.</li>
     * <li>The {@code maxGapOffset} defaults to an  integer of size {@code 10000}.</li>
     * <li>The {@code lowestGlobalSequence} defaults to a long of size {@code 1}.</li>
     * <li>The {@code gapTimeout} defaults to an integer of size {@code 60000} (1 minute).</li>
     * <li>The {@code gapCleaningThreshold} defaults to an integer of size {@code 250}.</li>
     * </ul>
     * <p>
     * The event and snapshot {@link Serializer}, the {@link EntityManagerProvider} and {@link TransactionManager} are
     * <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link JpaEventStorageEngine}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Converts an {@link EventMessage} to a {@link DomainEventMessage}. If the message already is a {@link
     * DomainEventMessage} it will be returned as is. Otherwise, a new {@link GenericDomainEventMessage} is made with
     * {@code null} type, {@code aggregateIdentifier} equal to {@code messageIdentifier} and sequence number of 0L.
     * <p>
     * Doing so allows using the {@link DomainEventEntry} to store both a {@link GenericEventMessage} and a {@link
     * GenericDomainEventMessage}.
     *
     * @param event the input event message
     * @param <T>   the type of payload in the message
     *
     * @return the message converted to a domain event message
     */
    protected static <T> DomainEventMessage<T> asDomainEventMessage(EventMessage<T> event) {
        return event instanceof DomainEventMessage<?>
               ? (DomainEventMessage<T>) event
               : new GenericDomainEventMessage<>(null, event.getIdentifier(), 0L, event, event::getTimestamp);
    }

    /**
     * Returns a batch of event data as object entries in the event storage with a
     * greater than the given {@code token}. Size of event is decided by {@link #batchSize()}.
     *
     * @param token Object describing the global index of the last processed event.
     * @return A batch of event messages as object stored since the given tracking token.
     */
    protected List<Object[]> fetchEvents(GapAwareTrackingToken token) {
        TypedQuery<Object[]> query;
        if (token == null || token.getGaps().isEmpty()) {
            query = entityManager().createQuery(
                    "SELECT e.globalIndex, e.type, e.aggregateIdentifier, e.sequenceNumber, e.eventIdentifier, "
                            + "e.timeStamp, e.payloadType, e.payloadRevision, e.payload, e.metaData " +
                            "FROM " + domainEventEntryEntityName() + " e " +
                            "WHERE e.globalIndex > :token ORDER BY e.globalIndex ASC", Object[].class);
        } else {
            query = entityManager().createQuery(
                    "SELECT e.globalIndex, e.type, e.aggregateIdentifier, e.sequenceNumber, e.eventIdentifier, "
                            + "e.timeStamp, e.payloadType, e.payloadRevision, e.payload, e.metaData " +
                            "FROM " + domainEventEntryEntityName() + " e " +
                            "WHERE e.globalIndex > :token OR e.globalIndex IN :gaps ORDER BY e.globalIndex ASC",
                    Object[].class
            ).setParameter("gaps", token.getGaps());
        }
        return query.setParameter("token", token == null ? -1L : token.getIndex())
                    .setMaxResults(batchSize())
                    .getResultList();
    }

    @Override
    protected List<? extends TrackedEventData<?>> fetchTrackedEvents(TrackingToken lastToken, int batchSize) {
        Assert.isTrue(
                lastToken == null || lastToken instanceof GapAwareTrackingToken,
                () -> String.format("Token [%s] is of the wrong type. Expected [%s]",
                                    lastToken, GapAwareTrackingToken.class.getSimpleName())
        );

        GapAwareTrackingToken previousToken = cleanedToken((GapAwareTrackingToken) lastToken);

        List<Object[]> entries = transactionManager.fetchInTransaction(() -> fetchEvents(previousToken));
        List<TrackedEventData<?>> result = new ArrayList<>();
        GapAwareTrackingToken token = previousToken;
        for (Object[] entry : entries) {
            long globalSequence = (Long) entry[0];
            String aggregateIdentifier = (String) entry[2];
            String eventIdentifier = (String) entry[4];
            GenericDomainEventEntry<?> domainEvent = new GenericDomainEventEntry<>(
                    (String) entry[1], eventIdentifier.equals(aggregateIdentifier) ? null : aggregateIdentifier,
                    (long) entry[3], eventIdentifier, entry[5],
                    (String) entry[6], (String) entry[7], entry[8], entry[9]
            );

            // Now that we have the event itself, we can calculate the token
            boolean allowGaps = domainEvent.getTimestamp().isAfter(gapTimeoutThreshold());
            if (token == null) {
                token = GapAwareTrackingToken.newInstance(
                        globalSequence,
                        allowGaps
                        ? LongStream.range(Math.min(lowestGlobalSequence, globalSequence), globalSequence)
                                    .boxed()
                                    .collect(Collectors.toCollection(TreeSet::new))
                        : Collections.emptySortedSet()
                );
            } else {
                token = token.advanceTo(globalSequence, maxGapOffset);
                if (!allowGaps) {
                    token = token.withGapsTruncatedAt(globalSequence);
                }
            }
            result.add(new TrackedDomainEventData<>(token, domainEvent));
        }
        return result;
    }

    private GapAwareTrackingToken cleanedToken(GapAwareTrackingToken lastToken) {
        if (lastToken != null && lastToken.getGaps().size() > gapCleaningThreshold) {
            return withGapsCleaned(lastToken, transactionManager.fetchInTransaction(() -> entityManager()
                    .createQuery(
                            "SELECT e.globalIndex, e.timeStamp FROM " + domainEventEntryEntityName() + " e "
                                    + "WHERE e.globalIndex >= :firstGapOffset "
                                    + "AND e.globalIndex <= :maxGlobalIndex",
                            Object[].class
                    )
                    .setParameter("firstGapOffset", lastToken.getGaps().first())
                    .setParameter("maxGlobalIndex", lastToken.getGaps().last() + 1L)
                    .getResultList()));
        }
        return lastToken;
    }

    private GapAwareTrackingToken withGapsCleaned(GapAwareTrackingToken token, List<Object[]> indexToTimestamp) {
        Instant gapTimeoutThreshold = gapTimeoutThreshold();
        GapAwareTrackingToken cleanedToken = token;
        for (Object[] result : indexToTimestamp) {
            try {
                Instant timestamp = DateTimeUtils.parseInstant(result[1].toString());
                long sequenceNumber = (long) result[0];
                if (cleanedToken.getGaps().contains(sequenceNumber) || timestamp.isAfter(gapTimeoutThreshold)) {
                    // filled a gap or found an entry that is too recent. Should not continue cleaning up
                    return cleanedToken;
                }
                if (cleanedToken.getGaps().contains(sequenceNumber - 1)) {
                    cleanedToken = cleanedToken.withGapsTruncatedAt(sequenceNumber);
                }
            } catch (DateTimeParseException e) {
                if (logger.isDebugEnabled()) {
                    logger.info("Unable to parse timestamp ('{}') to clean old gaps. Trying to proceed. ",
                                e.getParsedString(), e);
                } else {
                    logger.info("Unable to parse timestamp ('{}') to clean old gaps. Trying to proceed. " +
                                        "Exception message: {}. (enable debug logging for full stack trace)",
                                e.getParsedString(), e.getMessage());
                }
            }
        }
        return cleanedToken;
    }

    private Instant gapTimeoutThreshold() {
        return GenericEventMessage.clock.instant().minus(gapTimeout, ChronoUnit.MILLIS);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<? extends DomainEventData<?>> fetchDomainEvents(String aggregateIdentifier, long firstSequenceNumber,
                                                                   int batchSize) {
        return transactionManager.fetchInTransaction(
                () -> entityManager()
                        .createQuery(
                                "SELECT new org.axonframework.eventhandling.GenericDomainEventEntry(" +
                                        "e.type, e.aggregateIdentifier, e.sequenceNumber, e.eventIdentifier, e.timeStamp, "
                                        + "e.payloadType, e.payloadRevision, e.payload, e.metaData) FROM "
                                        + domainEventEntryEntityName() + " e WHERE e.aggregateIdentifier = :id "
                                        + "AND e.sequenceNumber >= :seq ORDER BY e.sequenceNumber ASC"
                        )
                        .setParameter("id", aggregateIdentifier)
                        .setParameter("seq", firstSequenceNumber)
                        .setMaxResults(batchSize)
                        .getResultList());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Stream<? extends DomainEventData<?>> readSnapshotData(String aggregateIdentifier) {
        return transactionManager.fetchInTransaction(
                () -> entityManager()
                        .createQuery(
                                "SELECT new org.axonframework.eventhandling.GenericDomainEventEntry("
                                        + "e.type, e.aggregateIdentifier, e.sequenceNumber, e.eventIdentifier, "
                                        + "e.timeStamp, e.payloadType, e.payloadRevision, e.payload, e.metaData) FROM "
                                        + snapshotEventEntryEntityName() + " e " + "WHERE e.aggregateIdentifier = :id "
                                        + "ORDER BY e.sequenceNumber DESC"
                        )
                        .setParameter("id", aggregateIdentifier)
                        .setMaxResults(1)
                        .getResultList()
                        .stream()
        );
    }

    @Override
    protected void appendEvents(List<? extends EventMessage<?>> events, Serializer serializer) {
        if (events.isEmpty()) {
            return;
        }

        transactionManager.executeInTransaction(() -> {
            try {
                events.stream().map(event -> createEventEntity(event, serializer)).forEach(entityManager()::persist);
                if (explicitFlush) {
                    entityManager().flush();
                }
            } catch (Exception e) {
                handlePersistenceException(e, events.get(0));
            }
        });
    }

    @Override
    protected void storeSnapshot(DomainEventMessage<?> snapshot, Serializer serializer) {
        try {
            entityManager().merge(createSnapshotEntity(snapshot, serializer));
            deleteSnapshots(snapshot.getAggregateIdentifier(), snapshot.getSequenceNumber());
            if (explicitFlush) {
                entityManager().flush();
            }
        } catch (Exception e) {
            handlePersistenceException(e, snapshot);
        }
    }

    @Override
    public Optional<Long> lastSequenceNumberFor(@Nonnull String aggregateIdentifier) {
        List<Long> results = entityManager().createQuery(
                                                    "SELECT MAX(e.sequenceNumber) FROM " + domainEventEntryEntityName()
                                                            + " e WHERE e.aggregateIdentifier = :aggregateId", Long.class)
                                            .setParameter("aggregateId", aggregateIdentifier)
                                            .getResultList();
        if (results.size() == 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(results.get(0));
    }

    @Override
    public TrackingToken createTailToken() {
        List<Long> results = entityManager().createQuery(
                "SELECT MIN(e.globalIndex) - 1 FROM " + domainEventEntryEntityName() + " e", Long.class
        ).getResultList();
        return createToken(results);
    }

    @Override
    public TrackingToken createHeadToken() {
        return createToken(mostRecentIndex());
    }

    @Override
    public TrackingToken createTokenAt(@Nonnull Instant dateTime) {
        List<Long> results = entityManager()
                .createQuery(
                        "SELECT MIN(e.globalIndex) - 1 FROM " + domainEventEntryEntityName() + " e "
                                + "WHERE e.timeStamp >= :dateTime", Long.class
                )
                .setParameter("dateTime", formatInstant(dateTime))
                .getResultList();

        return noTokenFound(results) ? createToken(mostRecentIndex()) : createToken(results);
    }

    private List<Long> mostRecentIndex() {
        return entityManager()
                .createQuery("SELECT MAX(e.globalIndex) FROM " + domainEventEntryEntityName() + " e", Long.class)
                .getResultList();
    }

    private TrackingToken createToken(List<Long> tokens) {
        if (noTokenFound(tokens)) {
            return null;
        }
        return GapAwareTrackingToken.newInstance(tokens.get(0), Collections.emptySet());
    }

    private boolean noTokenFound(List<Long> tokens) {
        return tokens.isEmpty() || tokens.get(0) == null;
    }

    /**
     * Deletes all snapshots from the underlying storage with given {@code aggregateIdentifier}.
     *
     * @param aggregateIdentifier the identifier of the aggregate to delete snapshots for
     * @param sequenceNumber      The sequence number from which value snapshots should be kept
     */
    protected void deleteSnapshots(String aggregateIdentifier, long sequenceNumber) {
        entityManager()
                .createQuery(
                        "DELETE FROM " + snapshotEventEntryEntityName() + " e "
                                + "WHERE e.aggregateIdentifier = :aggregateIdentifier "
                                + "AND e.sequenceNumber < :sequenceNumber"
                )
                .setParameter("aggregateIdentifier", aggregateIdentifier)
                .setParameter("sequenceNumber", sequenceNumber)
                .executeUpdate();
    }

    /**
     * Returns a Jpa event entity for given {@code eventMessage}. Use the given {@code serializer} to serialize the
     * payload and metadata of the event.
     *
     * @param eventMessage the event message to store
     * @param serializer   the serializer to serialize the payload and metadata
     *
     * @return the Jpa entity to be inserted
     */
    protected Object createEventEntity(EventMessage<?> eventMessage, Serializer serializer) {
        return new DomainEventEntry(asDomainEventMessage(eventMessage), serializer);
    }

    /**
     * Returns a Jpa snapshot entity for given {@code snapshot} of an aggregate. Use the given {@code serializer} to
     * serialize the payload and metadata of the snapshot event.
     *
     * @param snapshot   the domain event message containing a snapshot of the aggregate
     * @param serializer the serializer to serialize the payload and metadata
     *
     * @return the Jpa entity to be inserted
     */
    protected Object createSnapshotEntity(DomainEventMessage<?> snapshot, Serializer serializer) {
        return new SnapshotEventEntry(snapshot, serializer);
    }

    /**
     * Returns the name of the Jpa event entity. Defaults to 'DomainEventEntry'.
     *
     * @return the name of the Jpa event entity
     */
    protected String domainEventEntryEntityName() {
        return DomainEventEntry.class.getSimpleName();
    }

    /**
     * Returns the name of the Snapshot event entity. Defaults to 'SnapshotEventEntry'.
     *
     * @return the name of the Jpa snapshot entity
     */
    protected String snapshotEventEntryEntityName() {
        return SnapshotEventEntry.class.getSimpleName();
    }

    /**
     * Provides an {@link EntityManager} instance for storing and fetching event data.
     *
     * @return a provided entity manager
     */
    protected EntityManager entityManager() {
        return entityManagerProvider.getEntityManager();
    }

    /**
     * Sets the amount of time until a 'gap' in a TrackingToken may be considered timed out. This setting will affect
     * the cleaning process of gaps. Gaps that have timed out will be removed from Tracking Tokens to improve
     * performance of reading events. Defaults to 60000 (1 minute).
     *
     * @param gapTimeout The amount of time, in milliseconds until a gap may be considered timed out.
     */
    public void setGapTimeout(int gapTimeout) {
        this.gapTimeout = gapTimeout;
    }

    /**
     * Sets the threshold of number of gaps in a token before an attempt to clean gaps up is taken. Defaults to 250.
     *
     * @param gapCleaningThreshold The number of gaps before triggering a cleanup.
     */
    public void setGapCleaningThreshold(int gapCleaningThreshold) {
        this.gapCleaningThreshold = gapCleaningThreshold;
    }

    /**
     * Builder class to instantiate a {@link JpaEventStorageEngine}.
     * <p>
     * The following configurable fields have defaults:
     * <ul>
     * <li>The {@link EventUpcaster} defaults to an {@link org.axonframework.serialization.upcasting.event.NoOpEventUpcaster}.</li>
     * <li>The {@link PersistenceExceptionResolver} is defaulted to a {@link SQLErrorCodesResolver}, <b>if</b> the
     * {@link DataSource} is provided</li>
     * <li>The {@code snapshotFilter} defaults to a {@link SnapshotFilter#allowAll()} instance.</li>
     * <li>The {@code batchSize} defaults to an integer of size {@code 100}.</li>
     * <li>The {@code explicitFlush} defaults to {@code true}.</li>
     * <li>The {@code maxGapOffset} defaults to an  integer of size {@code 10000}.</li>
     * <li>The {@code lowestGlobalSequence} defaults to a long of size {@code 1}.</li>
     * <li>The {@code gapTimeout} defaults to an integer of size {@code 60000} (1 minute).</li>
     * <li>The {@code gapCleaningThreshold} defaults to an integer of size {@code 250}.</li>
     * </ul>
     * <p>
     * The event and snapshot {@link Serializer}, the {@link EntityManagerProvider} and {@link TransactionManager} are
     * <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder extends BatchingEventStorageEngine.Builder {

        private EntityManagerProvider entityManagerProvider;
        private TransactionManager transactionManager;
        private boolean explicitFlush = true;
        private int maxGapOffset = DEFAULT_MAX_GAP_OFFSET;
        private long lowestGlobalSequence = DEFAULT_LOWEST_GLOBAL_SEQUENCE;
        private int gapTimeout = DEFAULT_GAP_TIMEOUT;
        private int gapCleaningThreshold = DEFAULT_GAP_CLEANING_THRESHOLD;

        @Override
        public JpaEventStorageEngine.Builder snapshotSerializer(Serializer snapshotSerializer) {
            super.snapshotSerializer(snapshotSerializer);
            return this;
        }

        @Override
        public JpaEventStorageEngine.Builder upcasterChain(EventUpcaster upcasterChain) {
            super.upcasterChain(upcasterChain);
            return this;
        }

        @Override
        public JpaEventStorageEngine.Builder persistenceExceptionResolver(
                PersistenceExceptionResolver persistenceExceptionResolver
        ) {
            super.persistenceExceptionResolver(persistenceExceptionResolver);
            return this;
        }

        @Override
        public JpaEventStorageEngine.Builder eventSerializer(Serializer eventSerializer) {
            super.eventSerializer(eventSerializer);
            return this;
        }

        /**
         * {@inheritDoc}
         * <p>
         * The JpaEventStorageEngine defaults to using any batch smaller than the batch size as the final batch.
         */
        @Override
        public JpaEventStorageEngine.Builder finalAggregateBatchPredicate(Predicate<List<? extends DomainEventData<?>>> finalAggregateBatchPredicate) {
            super.finalAggregateBatchPredicate(finalAggregateBatchPredicate);
            return this;
        }

        /**
         * {@inheritDoc}
         *
         * @deprecated in favor of {@link #snapshotFilter(SnapshotFilter)}
         */
        @Override
        @Deprecated
        public JpaEventStorageEngine.Builder snapshotFilter(Predicate<? super DomainEventData<?>> snapshotFilter) {
            super.snapshotFilter(snapshotFilter);
            return this;
        }

        @Override
        public JpaEventStorageEngine.Builder snapshotFilter(SnapshotFilter snapshotFilter) {
            super.snapshotFilter(snapshotFilter);
            return this;
        }

        @Override
        public JpaEventStorageEngine.Builder batchSize(int batchSize) {
            super.batchSize(batchSize);
            return this;
        }

        /**
         * Sets the {@link PersistenceExceptionResolver} as a {@link SQLErrorCodesResolver}, using the provided
         * {@link DataSource} to resolve the error codes. <b>Note</b> that the provided DataSource sole purpose in this
         * {@link org.axonframework.eventsourcing.eventstore.EventStorageEngine} implementation is to be used for
         * instantiating the PersistenceExceptionResolver.
         *
         * @param dataSource the {@link DataSource} used to instantiate a
         *                   {@link SQLErrorCodesResolver#SQLErrorCodesResolver(DataSource)} as the
         *                   {@link PersistenceExceptionResolver}
         *
         * @return the current Builder instance, for fluent interfacing
         * @throws SQLException if creation of the {@link SQLErrorCodesResolver} fails
         */
        public Builder dataSource(DataSource dataSource) throws SQLException {
            persistenceExceptionResolver(new SQLErrorCodesResolver(dataSource));
            return this;
        }

        /**
         * Sets the {@link EntityManagerProvider} which provides the {@link EntityManager} used to access the
         * underlying database for this {@link org.axonframework.eventsourcing.eventstore.EventStorageEngine}
         * implementation.
         *
         * @param entityManagerProvider a {@link EntityManagerProvider} which provides the {@link EntityManager} used to
         *                              access the underlying database
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder entityManagerProvider(EntityManagerProvider entityManagerProvider) {
            assertNonNull(entityManagerProvider, "EntityManagerProvider may not be null");
            this.entityManagerProvider = entityManagerProvider;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to manage transaction around fetching event data. Required by
         * certain databases for reading blob data.
         *
         * @param transactionManager a {@link TransactionManager} used to manage transaction around fetching event data
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets the {@code explicitFlush} field specifying whether to explicitly call {@link EntityManager#flush()}
         * after inserting the Events published in this Unit of Work. If {@code false}, this instance relies
         * on the TransactionManager to flush data. Note that the {@code persistenceExceptionResolver} may not be able
         * to translate exceptions anymore. {@code false} should only be used to optimize performance for batch
         * operations. In other cases, {@code true} is recommended, which is also the default.
         *
         * @param explicitFlush a {@code boolean} specifying whether to explicitly call {@link EntityManager#flush()}
         *                      after inserting the Events published in this Unit of Work
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder explicitFlush(boolean explicitFlush) {
            this.explicitFlush = explicitFlush;
            return this;
        }

        /**
         * Sets the {@code maxGapOffset} specifying the maximum distance in sequence numbers between a missing event and
         * the event with the highest known index. If the gap is bigger it is assumed that the missing event will not be
         * committed to the store anymore. This event storage engine will no longer look for those events the next time
         * a batch is fetched. Defaults to an integer of {@code 10000}
         * ({@link JpaEventStorageEngine#DEFAULT_MAX_GAP_OFFSET}.
         *
         * @param maxGapOffset an {@code int} specifying the maximum distance in sequence numbers between a missing
         *                     event and the event with the highest known index
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder maxGapOffset(int maxGapOffset) {
            assertPositive(maxGapOffset, "maxGapOffset");
            this.maxGapOffset = maxGapOffset;
            return this;
        }

        /**
         * Sets the {@code lowestGlobalSequence} specifying the first expected auto generated sequence number. For most
         * data stores this is 1 unless the table has contained entries before. Defaults to a {@code long} of {@code 1}
         * ({@link JpaEventStorageEngine#DEFAULT_LOWEST_GLOBAL_SEQUENCE}).
         *
         * @param lowestGlobalSequence a {@code long} specifying the first expected auto generated sequence number
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder lowestGlobalSequence(long lowestGlobalSequence) {
            assertThat(lowestGlobalSequence,
                       number -> number > 0,
                       "The lowestGlobalSequence must be a positive number");
            this.lowestGlobalSequence = lowestGlobalSequence;
            return this;
        }

        /**
         * Sets the amount of time until a 'gap' in a TrackingToken may be considered timed out. This setting will
         * affect the cleaning process of gaps. Gaps that have timed out will be removed from Tracking Tokens to improve
         * performance of reading events. Defaults to an  integer of {@code 60000}
         * ({@link JpaEventStorageEngine#DEFAULT_GAP_TIMEOUT}), thus 1 minute.
         *
         * @param gapTimeout an {@code int} specifying the amount of time in milliseconds until a 'gap' in a
         *                   TrackingToken may be considered timed out
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder gapTimeout(int gapTimeout) {
            assertPositive(gapTimeout, "gapTimeout");
            this.gapTimeout = gapTimeout;
            return this;
        }

        /**
         * Sets the threshold of number of gaps in a token before an attempt to clean gaps up is taken. Defaults to an
         * integer of {@code 250} ({@link JpaEventStorageEngine#DEFAULT_GAP_CLEANING_THRESHOLD}).
         *
         * @param gapCleaningThreshold an {@code int} specifying the threshold of number of gaps in a token before an
         *                             attempt to clean gaps up is taken
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder gapCleaningThreshold(int gapCleaningThreshold) {
            assertPositive(gapCleaningThreshold, "gapCleaningThreshold");
            this.gapCleaningThreshold = gapCleaningThreshold;
            return this;
        }

        private void assertPositive(int num, final String numberDescription) {
            assertThat(num, number -> number > 0, "The " + numberDescription + " must be a positive number");
        }

        /**
         * Initializes a {@link JpaEventStorageEngine} as specified through this Builder.
         *
         * @return a {@link JpaEventStorageEngine} as specified through this Builder
         */
        public JpaEventStorageEngine build() {
            return new JpaEventStorageEngine(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        @Override
        protected void validate() throws AxonConfigurationException {
            super.validate();
            assertNonNull(entityManagerProvider,
                          "The EntityManagerProvider is a hard requirement and should be provided");
            assertNonNull(transactionManager, "The TransactionManager is a hard requirement and should be provided");
        }
    }
}
