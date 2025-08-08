/*
 * Copyright (c) 2010-2025. Axon Framework
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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import org.axonframework.common.Assert;
import org.axonframework.common.DateTimeUtils;
import org.axonframework.common.TypeReference;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TerminalEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AggregateBasedConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.AggregateBasedEventStorageEngineUtils;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.EmptyAppendTransaction;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.StreamSpliterator;
import org.axonframework.eventsourcing.eventstore.LegacyResources;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.eventstreaming.EventCriterion;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.serialization.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;
import static org.axonframework.common.BuilderUtils.assertPositive;
import static org.axonframework.common.BuilderUtils.assertThat;
import static org.axonframework.common.DateTimeUtils.formatInstant;
import static org.axonframework.common.ObjectUtils.getOrDefault;
import static org.axonframework.eventsourcing.eventstore.AggregateBasedEventStorageEngineUtils.*;


/**
 * An {@link EventStorageEngine} implementation that uses JPA to store and fetch events from an aggregate-based event
 * storage solution.
 * <p>
 * By default, the payload of events is stored as a serialized blob of bytes. Other columns are used to store meta-data
 * that allow quick finding of DomainEvents for a specific aggregate in the correct order.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class AggregateBasedJpaEventStorageEngine implements EventStorageEngine {

    private static final Logger logger = LoggerFactory.getLogger(AggregateBasedJpaEventStorageEngine.class);

    private static final String FIRST_TOKEN_QUERY = "SELECT MIN(e.globalIndex) - 1 FROM AggregateBasedEventEntry e";
    private static final String LATEST_TOKEN_QUERY = "SELECT MAX(e.globalIndex) FROM AggregateBasedEventEntry e";
    private static final String TOKEN_AT_QUERY = """
            SELECT MIN(e.globalIndex) - 1 \
            FROM AggregateBasedEventEntry e \
            WHERE e.timestamp >= :dateTime
            """;

    private final EntityManagerProvider entityManagerProvider;
    private final TransactionManager transactionManager;
    private final Converter converter;
    private final PersistenceExceptionResolver persistenceExceptionResolver;

    private final BatchingEventStorageOperations batchingOperations;
    private final GapAwareTrackingTokenOperations tokenOperations;

    /**
     * Constructs an {@code AggregateBasedJpaEventStorageEngine} with the given parameters.
     *
     * @param entityManagerProvider The {@link jakarta.persistence.EntityManager} provided for this storage solution.
     * @param transactionManager    The transaction manager, ensuring all operations to the storage solution occur
     *                              transactionally.
     * @param converter             The converter used to convert the {@link EventMessage#payload()} and
     *                              {@link EventMessage#metaData()} to a {@code byte[]}.
     * @param configurationOverride A unary operator that can customize the {@code AggregateBasedJpaEventStorageEngine}
     *                              under construction.
     */
    public AggregateBasedJpaEventStorageEngine(@Nonnull EntityManagerProvider entityManagerProvider,
                                               @Nonnull TransactionManager transactionManager,
                                               @Nonnull Converter converter,
                                               @Nonnull UnaryOperator<Customization> configurationOverride) {
        this.entityManagerProvider =
                requireNonNull(entityManagerProvider, "The entityManagerProvider may not be null.");
        this.transactionManager = requireNonNull(transactionManager, "The transactionManager may not be null.");
        this.converter = requireNonNull(converter, "The converter may not be null");

        var customization = requireNonNull(configurationOverride, "the configurationOverride may not be null.")
                .apply(Customization.withDefaultValues());

        this.tokenOperations = new GapAwareTrackingTokenOperations(
                customization.tokenGapsHandling().timeout(),
                logger
        );
        this.batchingOperations = new BatchingEventStorageOperations(
                transactionManager,
                new LegacyJpaEventStorageOperations(transactionManager, entityManagerProvider),
                tokenOperations,
                customization.batchSize(),
                customization.finalAggregateBatchPredicate(),
                true,
                customization.tokenGapsHandling().cleaningThreshold(),
                customization.lowestGlobalSequence(),
                customization.tokenGapsHandling().maxOffset()
        );
        this.persistenceExceptionResolver = customization.persistenceExceptionResolver();
    }

    /**
     * Returns an {@link EntityManager} from the {@link EntityManagerProvider}.
     * <p>
     * Internal use only.
     *
     * @return An {@link EntityManager} from the {@link EntityManagerProvider}.
     */
    private EntityManager entityManager() {
        return entityManagerProvider.getEntityManager();
    }

    @Override
    public CompletableFuture<AppendTransaction> appendEvents(@Nonnull AppendCondition condition,
                                                             @Nonnull List<TaggedEventMessage<?>> events) {
        try {
            assertValidTags(events);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(EmptyAppendTransaction.INSTANCE);
        }

        return CompletableFuture.completedFuture(new AppendTransaction() {

            private final AtomicBoolean txFinished = new AtomicBoolean(false);
            private final AggregateBasedConsistencyMarker preCommitConsistencyMarker =
                    AggregateBasedConsistencyMarker.from(condition);

            @Override
            public CompletableFuture<ConsistencyMarker> commit() {
                if (txFinished.getAndSet(true)) {
                    return CompletableFuture.failedFuture(new IllegalStateException(
                            "Already committed or rolled back"
                    ));
                }
                var aggregateSequencer = AggregateSequencer.with(preCommitConsistencyMarker);

                CompletableFuture<Void> txResult = new CompletableFuture<>();
                var tx = transactionManager.startTransaction();
                try {
                    entityManagerPersistEvents(aggregateSequencer, events);
                    tx.commit();
                    txResult.complete(null);
                } catch (Exception e) {
                    tx.rollback();
                    txResult.completeExceptionally(e);
                }

                var afterCommitConsistencyMarker = aggregateSequencer.forwarded();
                return txResult.exceptionallyCompose(
                                       e -> CompletableFuture.failedFuture(translateConflictException(e))
                               )
                               .thenApply(r -> afterCommitConsistencyMarker);
            }

            private Throwable translateConflictException(Throwable e) {
                Predicate<Throwable> isConflictException = (t) -> persistenceExceptionResolver != null
                        && t instanceof Exception ex
                        && persistenceExceptionResolver.isDuplicateKeyViolation(ex);
                return AggregateBasedEventStorageEngineUtils
                        .translateConflictException(preCommitConsistencyMarker, e, isConflictException);
            }

            @Override
            public void rollback() {
                txFinished.set(true);
            }
        });
    }

    private void entityManagerPersistEvents(
            AggregateSequencer aggregateSequencer,
            List<TaggedEventMessage<?>> events
    ) {
        var entityManager = entityManager();
        events.stream()
              .map(taggedEvent -> mapToEntry(taggedEvent, aggregateSequencer, converter))
              .forEach(entityManager::persist);
    }

    private static AggregateBasedEventEntry mapToEntry(TaggedEventMessage<?> taggedEvent,
                                                       AggregateSequencer aggregateSequencer,
                                                       Converter converter) {
        String aggregateIdentifier = resolveAggregateIdentifier(taggedEvent.tags());
        String aggregateType = resolveAggregateType(taggedEvent.tags());
        EventMessage<?> event = taggedEvent.event();
        boolean isAggregateEvent =
                aggregateIdentifier != null && aggregateType != null && !taggedEvent.tags().isEmpty();
        return new AggregateBasedEventEntry(event.identifier(),
                                            event.type().name(),
                                            event.type().version(),
                                            event.payloadAs(byte[].class, converter),
                                            converter.convert(event.metaData(), byte[].class),
                                            event.timestamp(),
                                            aggregateType,
                                            isAggregateEvent ? aggregateIdentifier : event.identifier(),
                                            isAggregateEvent ? aggregateSequencer.incrementAndGetSequenceOf(
                                                    aggregateIdentifier) : 0L);
    }

    @Override
    public MessageStream<EventMessage<?>> source(@Nonnull SourcingCondition condition) {
        CompletableFuture<Void> endOfStreams = new CompletableFuture<>();
        List<AggregateSource> aggregateSources = condition.criteria()
                                                          .flatten()
                                                          .stream()
                                                          .map(criterion -> this.aggregateSourceForCriterion(
                                                                  condition, criterion
                                                          ))
                                                          .toList();

        return aggregateSources.stream()
                               .map(AggregateSource::source)
                               .reduce(MessageStream.empty().cast(), MessageStream::concatWith)
                               .whenComplete(() -> endOfStreams.complete(null))
                               .concatWith(MessageStream.fromFuture(
                                       endOfStreams.thenApply(event -> TerminalEventMessage.INSTANCE),
                                       unused -> Context.with(
                                               ConsistencyMarker.RESOURCE_KEY,
                                               combineAggregateMarkers(aggregateSources.stream())
                                       )
                               ));
    }

    private AggregateSource aggregateSourceForCriterion(SourcingCondition condition, EventCriterion criterion) {
        AtomicReference<AggregateBasedConsistencyMarker> markerReference = new AtomicReference<>();
        var aggregateIdentifier = resolveAggregateIdentifier(criterion.tags());
        Stream<? extends AggregateBasedEventEntry> events =
                batchingOperations.readEventData(aggregateIdentifier, condition.start());

        MessageStream<EventMessage<?>> source =
                MessageStream.fromStream(events,
                                         this::convertToEventMessage,
                                         event -> setMarkerAndBuildContext(event.aggregateIdentifier(),
                                                                           event.aggregateSequenceNumber(),
                                                                           event.aggregateType(),
                                                                           markerReference))
                             // Defaults the marker when the aggregate stream was empty
                             .whenComplete(() -> markerReference.compareAndSet(
                                     null, new AggregateBasedConsistencyMarker(aggregateIdentifier, 0)
                             ))
                             .cast();
        return new AggregateSource(markerReference, source);
    }

    private GenericEventMessage<?> convertToEventMessage(AggregateBasedEventEntry entry) {
        return new GenericEventMessage<>(entry.identifier(),
                                         new MessageType(entry.type(), entry.version()),
                                         entry.payload(),
                                         converter.convert(entry.metadata(), new TypeReference<Map<String, String>>() {
                                         }.getType()),
                                         DateTimeUtils.parseInstant(entry.timestamp()));
    }

    private static Context setMarkerAndBuildContext(String aggregateIdentifier,
                                                    long sequenceNumber,
                                                    String aggregateType,
                                                    AtomicReference<AggregateBasedConsistencyMarker> markerReference) {
        markerReference.set(new AggregateBasedConsistencyMarker(aggregateIdentifier, sequenceNumber));
        return buildContext(aggregateIdentifier, sequenceNumber, aggregateType);
    }

    private static ConsistencyMarker combineAggregateMarkers(Stream<AggregateSource> resultStream) {
        return resultStream.map(AggregateSource::markerReference)
                           .map(AtomicReference::get)
                           .map(marker -> (ConsistencyMarker) marker)
                           .reduce(ConsistencyMarker::upperBound)
                           .orElseThrow();
    }

    @Override
    public MessageStream<EventMessage<?>> stream(@Nonnull StreamingCondition condition) {
        var trackingToken = tokenOperations.assertGapAwareTrackingToken(condition.position());
        Stream<? extends TokenEntry> events = batchingOperations.readEventData(trackingToken);
        return MessageStream.fromStream(events,
                                        tokenEntry -> convertToEventMessage(tokenEntry.entry()),
                                        AggregateBasedJpaEventStorageEngine::trackedEventContext);
    }

    private static Context trackedEventContext(TokenEntry trackedEventData) {
        var context = Context.empty();
        context = buildContext(trackedEventData.entry().aggregateIdentifier(),
                               trackedEventData.entry().aggregateSequenceNumber(),
                               trackedEventData.entry().aggregateType());
        return context.withResource(TrackingToken.RESOURCE_KEY, trackedEventData.token);
    }

    private static Context buildContext(@Nonnull String aggregateIdentifier,
                                        @Nonnull Long aggregateSequenceNumber,
                                        @Nullable String aggregateType) {
        Context context = Context.with(LegacyResources.AGGREGATE_IDENTIFIER_KEY, aggregateIdentifier)
                                 .withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY, aggregateSequenceNumber);
        return aggregateType != null
                ? context.withResource(LegacyResources.AGGREGATE_TYPE_KEY, aggregateType)
                : context;
    }

    @Override
    public CompletableFuture<TrackingToken> firstToken() {
        return queryToken(FIRST_TOKEN_QUERY);
    }

    @Override
    public CompletableFuture<TrackingToken> latestToken() {
        return queryToken(LATEST_TOKEN_QUERY);
    }

    @Nonnull
    private CompletableFuture<TrackingToken> queryToken(String firstTokenQuery) {
        try (EntityManager entityManager = entityManager()) {
            List<Long> results = entityManager.createQuery(firstTokenQuery, Long.class).getResultList();
            if (results.isEmpty() || results.getFirst() == null) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.completedFuture(new GapAwareTrackingToken(results.getFirst(), Set.of()));
        }
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at) {
        try (EntityManager entityManager = entityManager()) {
            List<Long> results = entityManager.createQuery(TOKEN_AT_QUERY, Long.class)
                                              .setParameter("dateTime", formatInstant(at))
                                              .getResultList();
            if (results.isEmpty() || results.getFirst() == null) {
                return latestToken();
            }
            Long position = results.getFirst();
            return CompletableFuture.completedFuture(new GapAwareTrackingToken(position, Set.of()));
        }
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("entityManagerProvider", entityManagerProvider);
        descriptor.describeProperty("transactionManager", transactionManager);
        descriptor.describeProperty("converter", converter);
        descriptor.describeProperty("persistenceExceptionResolver", persistenceExceptionResolver);
        descriptor.describeProperty("tokenOperations", tokenOperations);
        descriptor.describeProperty("batchingOperations", batchingOperations);
    }

    private record BatchingEventStorageOperations(TransactionManager transactionManager,
                                                  LegacyJpaEventStorageOperations legacyJpaOperations,
                                                  GapAwareTrackingTokenOperations tokenOperations,
                                                  int batchSize,
                                                  Predicate<List<? extends AggregateBasedEventEntry>> finalAggregateBatchPredicate,
                                                  boolean fetchForAggregateUntilEmpty,
                                                  int gapCleaningThreshold,
                                                  long lowestGlobalSequence,
                                                  int maxGapOffset
    ) {

        /**
         * The batch optimization is intended to *not* retrieve a second batch of events to cover for potential gaps in
         * the first batch. This optimization is desirable for aggregate event streams, as these close once the end is
         * reached. For token-based event reading the stream does not necessarily close once reaching the end, thus the
         * optimization will block further event retrieval.
         */
        private static final boolean BATCH_OPTIMIZATION_DISABLED = false;

        private BatchingEventStorageOperations(
                TransactionManager transactionManager,
                LegacyJpaEventStorageOperations legacyJpaOperations,
                GapAwareTrackingTokenOperations tokenOperations,
                int batchSize,
                Predicate<List<? extends AggregateBasedEventEntry>> finalAggregateBatchPredicate,
                boolean fetchForAggregateUntilEmpty,
                int gapCleaningThreshold,
                long lowestGlobalSequence,
                int maxGapOffset
        ) {
            this.transactionManager = transactionManager;
            this.legacyJpaOperations = legacyJpaOperations;
            this.tokenOperations = tokenOperations;
            this.batchSize = batchSize;
            this.finalAggregateBatchPredicate = getOrDefault(finalAggregateBatchPredicate,
                                                             this::defaultFinalAggregateBatchPredicate);
            this.fetchForAggregateUntilEmpty = fetchForAggregateUntilEmpty;
            this.gapCleaningThreshold = gapCleaningThreshold;
            this.lowestGlobalSequence = lowestGlobalSequence;
            this.maxGapOffset = maxGapOffset;
        }

        Stream<? extends AggregateBasedEventEntry> readEventData(String identifier, long firstSequenceNumber) {
            StreamSpliterator<? extends AggregateBasedEventEntry> spliterator = new StreamSpliterator<>(
                    lastItem -> transactionManager.fetchInTransaction(
                            () -> legacyJpaOperations.fetchDomainEvents(
                                    identifier,
                                    lastItem == null ? firstSequenceNumber : lastItem.aggregateSequenceNumber() + 1,
                                    batchSize
                            )
                    )
                    , finalAggregateBatchPredicate);
            return StreamSupport.stream(spliterator, false);
        }

        Stream<? extends AggregateBasedJpaEventStorageEngine.TokenEntry> readEventData(TrackingToken trackingToken) {
            StreamSpliterator<? extends TokenEntry> spliterator = new StreamSpliterator<>(
                    lastItem -> fetchTrackedEvents(lastItem == null ? trackingToken : lastItem.token(), batchSize),
                    batch -> BATCH_OPTIMIZATION_DISABLED
            );
            return StreamSupport.stream(spliterator, false);
        }

        private List<AggregateBasedJpaEventStorageEngine.TokenEntry> fetchTrackedEvents(TrackingToken lastToken,
                                                                                        int batchSize) {
            Assert.isTrue(
                    lastToken == null || lastToken instanceof GapAwareTrackingToken,
                    () -> String.format("Token [%s] is of the wrong type. Expected [%s]",
                                        lastToken, GapAwareTrackingToken.class.getSimpleName())
            );

            GapAwareTrackingToken previousToken = cleanedToken((GapAwareTrackingToken) lastToken);

            List<Object[]> entries = transactionManager.fetchInTransaction(
                    () -> legacyJpaOperations.fetchEvents(previousToken, batchSize)
            );
            return entriesToEvents(previousToken,
                                   entries,
                                   tokenOperations.gapTimeoutThreshold(),
                                   lowestGlobalSequence,
                                   maxGapOffset);
        }

        List<AggregateBasedJpaEventStorageEngine.TokenEntry> entriesToEvents(
                GapAwareTrackingToken previousToken,
                List<Object[]> entries,
                Instant gapTimeoutThreshold,
                long lowestGlobalSequence,
                int maxGapOffset
        ) {
            List<AggregateBasedJpaEventStorageEngine.TokenEntry> result = new ArrayList<>();
            GapAwareTrackingToken token = previousToken;
            for (Object[] entry : entries) {
                long globalIndex = (Long) entry[0];
                Instant timestamp = DateTimeUtils.parseInstant((String) entry[6]);
                AggregateBasedEventEntry eventEntry = new AggregateBasedEventEntry(
                        (String) entry[1], // identifier
                        (String) entry[2], // type
                        (String) entry[3], // version
                        (byte[]) entry[4], // payload
                        (byte[]) entry[5], // metadata
                        timestamp, // timestamp
                        (String) entry[7], // aggregate type
                        (String) entry[8], // aggregate identifier
                        (Long) entry[9] // aggregate sequence number
                );

                // Now that we have the event itself, we can calculate the token
                boolean allowGaps = timestamp.isAfter(gapTimeoutThreshold);
                if (token == null) {
                    token = GapAwareTrackingToken.newInstance(
                            globalIndex,
                            allowGaps
                                    ? LongStream.range(Math.min(lowestGlobalSequence, globalIndex), globalIndex)
                                                .boxed()
                                                .collect(Collectors.toCollection(TreeSet::new))
                                    : Collections.emptySortedSet()
                    );
                } else {
                    token = token.advanceTo(globalIndex, allowGaps ? maxGapOffset : 0);
                }
                result.add(new AggregateBasedJpaEventStorageEngine.TokenEntry(token, eventEntry));
            }
            return result;
        }

        private GapAwareTrackingToken cleanedToken(GapAwareTrackingToken lastToken) {
            if (lastToken != null && lastToken.getGaps().size() > gapCleaningThreshold) {
                return tokenOperations.withGapsCleaned(lastToken, indexAndTimestampBetweenGaps(lastToken));
            }
            return lastToken;
        }

        private List<Object[]> indexAndTimestampBetweenGaps(GapAwareTrackingToken lastToken) {
            return transactionManager.fetchInTransaction(() -> legacyJpaOperations.indexAndTimestampBetweenGaps(
                    lastToken));
        }


        private boolean defaultFinalAggregateBatchPredicate(List<? extends AggregateBasedEventEntry> recentBatch) {
            return fetchForAggregateUntilEmpty() ? recentBatch.isEmpty() : recentBatch.size() < batchSize;
        }
    }

    /**
     * A tuple of an {@link AtomicReference} to an {@link AggregateBasedConsistencyMarker} and a {@link MessageStream},
     * used when sourcing events from an aggregate-specific {@link EventCriterion}. This tuple object can then be used
     * to {@link MessageStream#concatWith(MessageStream) construct a single stream}, completing with a final marker.
     */
    private record AggregateSource(
            AtomicReference<AggregateBasedConsistencyMarker> markerReference,
            MessageStream<EventMessage<?>> source
    ) {

    }

    private record TokenEntry(GapAwareTrackingToken token, AggregateBasedEventEntry entry) {

    }

    public record Customization(
            PersistenceExceptionResolver persistenceExceptionResolver,
            int batchSize,
            Predicate<List<? extends AggregateBasedEventEntry>> finalAggregateBatchPredicate,
            long lowestGlobalSequence,
            TokenGapsHandlingConfig tokenGapsHandling
    ) {

        private static final int DEFAULT_BATCH_SIZE = 100;
        private static final long DEFAULT_LOWEST_GLOBAL_SEQUENCE = 1;

        public Customization {
            assertThat(batchSize, size -> size > 0, "The batchSize must be a positive number");
            assertThat(lowestGlobalSequence,
                       number -> number > 0,
                       "The lowestGlobalSequence must be a positive number");
        }

        public record TokenGapsHandlingConfig(int maxOffset, int timeout, int cleaningThreshold) {

            private static final int DEFAULT_MAX_GAP_OFFSET = 10000;
            private static final int DEFAULT_GAP_TIMEOUT = 60000;
            private static final int DEFAULT_GAP_CLEANING_THRESHOLD = 250;

            public TokenGapsHandlingConfig {
                assertPositive(maxOffset, "maxOffset");
                assertPositive(timeout, "timeout");
                assertPositive(cleaningThreshold, "cleaningThreshold");
            }

            static TokenGapsHandlingConfig withDefaultValues() {
                return new TokenGapsHandlingConfig(DEFAULT_MAX_GAP_OFFSET,
                                                   DEFAULT_GAP_TIMEOUT,
                                                   DEFAULT_GAP_CLEANING_THRESHOLD);
            }
        }

        public static Customization withDefaultValues() {
            return new Customization(
                    null,
                    DEFAULT_BATCH_SIZE,
                    null,
                    DEFAULT_LOWEST_GLOBAL_SEQUENCE,
                    TokenGapsHandlingConfig.withDefaultValues()
            );
        }

        public Customization persistenceExceptionResolver(PersistenceExceptionResolver persistenceExceptionResolver) {
            return new Customization(
                    persistenceExceptionResolver,
                    batchSize,
                    finalAggregateBatchPredicate,
                    lowestGlobalSequence,
                    tokenGapsHandling
            );
        }

        public Customization batchSize(int batchSize) {
            return new Customization(
                    persistenceExceptionResolver,
                    batchSize,
                    finalAggregateBatchPredicate,
                    lowestGlobalSequence,
                    tokenGapsHandling
            );
        }

        public Customization finalAggregateBatchPredicate(
                Predicate<List<? extends AggregateBasedEventEntry>> finalAggregateBatchPredicate
        ) {
            return new Customization(
                    persistenceExceptionResolver,
                    batchSize,
                    finalAggregateBatchPredicate,
                    lowestGlobalSequence,
                    tokenGapsHandling
            );
        }

        public Customization lowestGlobalSequence(long lowestGlobalSequence) {
            return new Customization(
                    persistenceExceptionResolver,
                    batchSize,
                    finalAggregateBatchPredicate,
                    lowestGlobalSequence,
                    tokenGapsHandling
            );
        }

        public Customization tokenGapsHandling(UnaryOperator<TokenGapsHandlingConfig> configurationOverride) {
            return new Customization(
                    persistenceExceptionResolver,
                    batchSize,
                    finalAggregateBatchPredicate,
                    lowestGlobalSequence,
                    configurationOverride.apply(tokenGapsHandling)
            );
        }
    }
}
