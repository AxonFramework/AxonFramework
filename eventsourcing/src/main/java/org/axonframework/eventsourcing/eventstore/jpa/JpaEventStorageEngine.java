/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.sql.DataSource;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * EventStorageEngine implementation that uses JPA to store and fetch events.
 * <p>
 * By default, the payload of events is stored as a serialized blob of bytes. Other columns are used to store meta-data
 * that allow quick finding of DomainEvents for a specific aggregate in the correct order.
 *
 * @author Rene de Waele
 * @since 3.0
 * @deprecated Will be removed in version 5.0.0. The
 * {@link org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine} implementations should be used instead.
 */
@Deprecated
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

    private final LegacyJpaEventStorageOperations legacyJpaOperations;
    private final GapAwareTrackingTokenOperations tokenOperations;

    /**
     * Instantiate a {@link JpaEventStorageEngine} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the event and snapshot {@link Serializer}, the {@link EntityManagerProvider} and
     * {@link TransactionManager} are not {@code null}, and will throw an {@link AxonConfigurationException} if any of
     * them is {@code null}.
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

        this.legacyJpaOperations = new LegacyJpaEventStorageOperations(
                transactionManager,
                entityManagerProvider.getEntityManager(),
                domainEventEntryEntityName(),
                snapshotEventEntryEntityName()
        );
        this.tokenOperations = new GapAwareTrackingTokenOperations(gapTimeout, logger);
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
     * Converts an {@link EventMessage} to a {@link DomainEventMessage}. If the message already is a
     * {@link DomainEventMessage} it will be returned as is. Otherwise, a new {@link GenericDomainEventMessage} is made
     * with {@code null} type, {@code aggregateIdentifier} equal to {@code messageIdentifier} and sequence number of
     * 0L.
     * <p>
     * Doing so allows using the {@link DomainEventEntry} to store both a {@link GenericEventMessage} and a
     * {@link GenericDomainEventMessage}.
     *
     * @param event the input event message
     * @param <T>   the type of payload in the message
     * @return the message converted to a domain event message
     */
    protected static <T> DomainEventMessage<T> asDomainEventMessage(EventMessage<T> event) {
        return event instanceof DomainEventMessage<?>
                ? (DomainEventMessage<T>) event
                : new GenericDomainEventMessage<>(null, event.getIdentifier(), 0L, event, event::getTimestamp);
    }

    /**
     * Returns a batch of event data as object entries in the event storage with a greater than the given {@code token}.
     * Size of event is decided by {@link #batchSize()}.
     *
     * @param token Object describing the global index of the last processed event.
     * @return A batch of event messages as object stored since the given tracking token.
     */
    protected List<Object[]> fetchEvents(GapAwareTrackingToken token) {
        return legacyJpaOperations.fetchEvents(token, batchSize());
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
        return legacyJpaOperations.entriesToEvents(
                previousToken, entries, tokenOperations.gapTimeoutThreshold(), lowestGlobalSequence, maxGapOffset
        );
    }

    private GapAwareTrackingToken cleanedToken(GapAwareTrackingToken lastToken) {
        if (lastToken != null && lastToken.getGaps().size() > gapCleaningThreshold) {
            return tokenOperations.withGapsCleaned(lastToken, indexToTimestamp(lastToken));
        }
        return lastToken;
    }

    private List<Object[]> indexToTimestamp(GapAwareTrackingToken lastToken) {
        return transactionManager.fetchInTransaction(() -> legacyJpaOperations.indexToTimestamp(lastToken));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<? extends DomainEventData<?>> fetchDomainEvents(String aggregateIdentifier, long firstSequenceNumber,
                                                                   int batchSize) {
        return transactionManager.fetchInTransaction(
                () -> legacyJpaOperations.fetchDomainEvents(aggregateIdentifier, firstSequenceNumber, batchSize)
        );
    }

    @Override
    protected Stream<? extends DomainEventData<?>> readSnapshotData(String aggregateIdentifier) {
        return transactionManager.fetchInTransaction(
                () -> legacyJpaOperations.readSnapshotData(aggregateIdentifier).stream()
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
        return legacyJpaOperations.lastSequenceNumberFor(aggregateIdentifier);
    }

    @Override
    public TrackingToken createTailToken() {
        return legacyJpaOperations.minGlobalIndex()
                                  .flatMap(this::gapAwareTrackingTokenOn)
                                  .orElse(null);
    }

    @Override
    public TrackingToken createHeadToken() {
        return legacyJpaOperations.maxGlobalIndex()
                                  .flatMap(this::gapAwareTrackingTokenOn)
                                  .orElse(null);
    }

    @Override
    public TrackingToken createTokenAt(@Nonnull Instant dateTime) {
        return legacyJpaOperations.globalIndexAt(dateTime)
                                  .flatMap(this::gapAwareTrackingTokenOn)
                                  .or(() -> legacyJpaOperations.maxGlobalIndex()
                                                               .flatMap(this::gapAwareTrackingTokenOn))
                                  .orElse(null);
    }

    private Optional<TrackingToken> gapAwareTrackingTokenOn(Long token) {
        return token == null
                ? Optional.empty()
                : Optional.of(GapAwareTrackingToken.newInstance(token, Collections.emptySet()));
    }

    /**
     * Deletes all snapshots from the underlying storage with given {@code aggregateIdentifier}.
     *
     * @param aggregateIdentifier the identifier of the aggregate to delete snapshots for
     * @param sequenceNumber      The sequence number from which value snapshots should be kept
     */
    protected void deleteSnapshots(String aggregateIdentifier, long sequenceNumber) {
        legacyJpaOperations.deleteSnapshots(aggregateIdentifier, sequenceNumber);
    }

    /**
     * Returns a Jpa event entity for given {@code eventMessage}. Use the given {@code serializer} to serialize the
     * payload and metadata of the event.
     *
     * @param eventMessage the event message to store
     * @param serializer   the serializer to serialize the payload and metadata
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
        public JpaEventStorageEngine.Builder finalAggregateBatchPredicate(
                Predicate<List<? extends DomainEventData<?>>> finalAggregateBatchPredicate) {
            super.finalAggregateBatchPredicate(finalAggregateBatchPredicate);
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
         * @return the current Builder instance, for fluent interfacing
         * @throws SQLException if creation of the {@link SQLErrorCodesResolver} fails
         */
        public Builder dataSource(DataSource dataSource) throws SQLException {
            persistenceExceptionResolver(new SQLErrorCodesResolver(dataSource));
            return this;
        }

        /**
         * Sets the {@link EntityManagerProvider} which provides the {@link EntityManager} used to access the underlying
         * database for this {@link org.axonframework.eventsourcing.eventstore.EventStorageEngine} implementation.
         *
         * @param entityManagerProvider a {@link EntityManagerProvider} which provides the {@link EntityManager} used to
         *                              access the underlying database
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
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets the {@code explicitFlush} field specifying whether to explicitly call {@link EntityManager#flush()}
         * after inserting the Events published in this Unit of Work. If {@code false}, this instance relies on the
         * TransactionManager to flush data. Note that the {@code persistenceExceptionResolver} may not be able to
         * translate exceptions anymore. {@code false} should only be used to optimize performance for batch operations.
         * In other cases, {@code true} is recommended, which is also the default.
         *
         * @param explicitFlush a {@code boolean} specifying whether to explicitly call {@link EntityManager#flush()}
         *                      after inserting the Events published in this Unit of Work
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
