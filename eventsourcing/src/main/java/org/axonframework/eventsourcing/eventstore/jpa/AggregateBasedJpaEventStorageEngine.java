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
import jakarta.persistence.TypedQuery;
import org.axonframework.common.Assert;
import org.axonframework.common.TypeReference;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TerminalEventMessage;
import org.axonframework.eventhandling.conversion.EventConverter;
import org.axonframework.eventhandling.processors.streaming.token.GapAwareTrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AggregateBasedConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.AggregateBasedEventStorageEngineUtils;
import org.axonframework.eventsourcing.eventstore.AggregateBasedEventStorageEngineUtils.AggregateSequencer;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.EmptyAppendTransaction;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.messaging.LegacyResources;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.StreamSpliterator;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.eventstreaming.EventCriterion;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.eventstreaming.Tag;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.serialization.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import static org.axonframework.common.DateTimeUtils.formatInstant;
import static org.axonframework.eventsourcing.eventstore.AggregateBasedEventStorageEngineUtils.assertValidTags;
import static org.axonframework.eventsourcing.eventstore.AggregateBasedEventStorageEngineUtils.resolveAggregateIdentifier;
import static org.axonframework.eventsourcing.eventstore.AggregateBasedEventStorageEngineUtils.resolveAggregateType;


/**
 * An {@link EventStorageEngine} implementation that uses JPA to store and fetch events from an aggregate-based event
 * storage solution.
 * <p>
 * By default, the payload of events is stored as a converted blob of bytes. Other columns are used to store meta-data
 * that allow quick finding of events for a specific aggregates in the correct order.
 *
 * @author Mateusz Nowak
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class AggregateBasedJpaEventStorageEngine implements EventStorageEngine {

    private static final Logger logger = LoggerFactory.getLogger(AggregateBasedJpaEventStorageEngine.class);

    /**
     * The batch optimization is intended to *not* retrieve a second batch of events to cover for potential gaps in the
     * first batch. This optimization is desirable for aggregate event streams, as these close once the end is reached.
     * For token-based event reading the stream does not necessarily close once reaching the end, thus the optimization
     * will block further event retrieval.
     */
    private static final boolean BATCH_OPTIMIZATION_DISABLED = false;

    private static final TypeReference<Map<String, String>> METADATA_MAP_TYPE_REF = new TypeReference<>() {
    };

    private static final String FIRST_TOKEN_QUERY = "SELECT MIN(e.globalIndex) - 1 FROM AggregateEventEntry e";
    private static final String LATEST_TOKEN_QUERY = "SELECT MAX(e.globalIndex) FROM AggregateEventEntry e";
    private static final String TOKEN_AT_QUERY = """
            SELECT MIN(e.globalIndex) - 1 \
            FROM AggregateEventEntry e \
            WHERE e.timestamp >= :dateTime""";
    private static final String EVENTS_BY_AGGREGATE_QUERY = """
            SELECT e \
            FROM AggregateEventEntry e \
            WHERE e.aggregateIdentifier = :id \
            AND e.aggregateSequenceNumber >= :seq \
            ORDER BY e.aggregateSequenceNumber ASC""";
    private static final String EVENTS_BY_TOKEN_QUERY = """
            SELECT e \
            FROM AggregateEventEntry e \
            WHERE e.globalIndex > :token \
            ORDER BY e.globalIndex ASC""";
    private static final String EVENTS_BY_GAPPED_TOKEN = """
            SELECT e \
            FROM AggregateEventEntry e \
            WHERE e.globalIndex > :token \
            OR e.globalIndex \
            IN :gaps \
            ORDER BY e.globalIndex ASC""";
    private static final String INDEX_AND_TIMESTAMP_QUERY = """
            SELECT e.globalIndex, e.timestamp
            FROM AggregateEventEntry e \
            WHERE e.globalIndex >= :firstGapOffset \
            AND e.globalIndex <= :maxGlobalIndex""";

    private final EntityManagerProvider entityManagerProvider;
    private final TransactionManager transactionManager;
    private final EventConverter converter;

    private final PersistenceExceptionResolver persistenceExceptionResolver;
    private final Predicate<List<? extends AggregateEventEntry>> finalBatchPredicate;
    private final int batchSize;
    private final int gapCleaningThreshold;
    private final int maxGapOffset;
    private final long lowestGlobalSequence;

    private final GapAwareTrackingTokenOperations tokenOperations;

    /**
     * Constructs an {@code AggregateBasedJpaEventStorageEngine} with the given parameters.
     *
     * @param entityManagerProvider The {@link jakarta.persistence.EntityManager} provided for this storage solution.
     * @param transactionManager    The transaction manager, ensuring all operations to the storage solution occur
     *                              transactionally.
     * @param converter             The converter used to convert the {@link EventMessage#payload()} and
     *                              {@link EventMessage#metadata()} to a {@code byte[]}.
     * @param configurer            A unary operator of the {@link AggregateBasedJpaEventStorageEngineConfiguration}
     *                              that customizes the {@code AggregateBasedJpaEventStorageEngine} under construction.
     */
    public AggregateBasedJpaEventStorageEngine(@Nonnull EntityManagerProvider entityManagerProvider,
                                               @Nonnull TransactionManager transactionManager,
                                               @Nonnull EventConverter converter,
                                               @Nonnull UnaryOperator<AggregateBasedJpaEventStorageEngineConfiguration> configurer) {
        this.entityManagerProvider =
                requireNonNull(entityManagerProvider, "The entityManagerProvider may not be null.");
        this.transactionManager = requireNonNull(transactionManager, "The transactionManager may not be null.");
        this.converter = requireNonNull(converter, "The converter may not be null.");

        var config = requireNonNull(configurer, "the configurationOverride may not be null.")
                .apply(AggregateBasedJpaEventStorageEngineConfiguration.DEFAULT);
        this.persistenceExceptionResolver = config.persistenceExceptionResolver();
        this.finalBatchPredicate = config.finalBatchPredicate();
        this.batchSize = config.batchSize();
        this.gapCleaningThreshold = config.gapCleaningThreshold();
        this.lowestGlobalSequence = config.lowestGlobalSequence();
        this.maxGapOffset = config.maxGapOffset();

        this.tokenOperations = new GapAwareTrackingTokenOperations(config.gapTimeout(), logger);
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
    public CompletableFuture<AppendTransaction<?>> appendEvents(@Nonnull AppendCondition condition,
                                                                @Nullable ProcessingContext processingContext,
                                                                @Nonnull List<TaggedEventMessage<?>> events) {
        try {
            assertValidTags(events);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(EmptyAppendTransaction.INSTANCE);
        }

        return CompletableFuture.completedFuture(new AppendTransaction<AggregateBasedConsistencyMarker>() {

            private final AtomicBoolean txFinished = new AtomicBoolean(false);
            private final AggregateBasedConsistencyMarker preCommitConsistencyMarker =
                    AggregateBasedConsistencyMarker.from(condition);

            @Override
            public CompletableFuture<AggregateBasedConsistencyMarker> commit(@Nullable ProcessingContext context) {
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

                return txResult.exceptionallyCompose(
                                       e -> CompletableFuture.failedFuture(translateConflictException(e))
                               )
                               .thenApply(v -> aggregateSequencer.forwarded());
            }

            @Override
            public CompletableFuture<ConsistencyMarker> afterCommit(@Nonnull AggregateBasedConsistencyMarker marker, @Nullable ProcessingContext context) {
                return CompletableFuture.completedFuture(marker);
            }

            private Throwable translateConflictException(Throwable e) {
                Predicate<Throwable> isConflictException = (t) -> persistenceExceptionResolver != null
                        && t instanceof Exception ex
                        && persistenceExceptionResolver.isDuplicateKeyViolation(ex);
                return AggregateBasedEventStorageEngineUtils
                        .translateConflictException(preCommitConsistencyMarker, e, isConflictException);
            }

            @Override
            public void rollback(@Nullable ProcessingContext context) {
                txFinished.set(true);
            }
        });
    }

    private void entityManagerPersistEvents(AggregateSequencer aggregateSequencer,
                                            List<TaggedEventMessage<?>> events) {
        try (EntityManager entityManager = entityManager()) {
            events.stream()
                  .map(taggedEvent -> mapToEntry(taggedEvent, aggregateSequencer, converter))
                  .forEach(entityManager::persist);
        }
    }

    private static AggregateEventEntry mapToEntry(TaggedEventMessage<?> taggedEvent,
                                                  AggregateSequencer aggregateSequencer,
                                                  Converter converter) {
        EventMessage event = taggedEvent.event();
        Set<Tag> tags = taggedEvent.tags();
        String aggregateIdentifier = resolveAggregateIdentifier(tags);
        return new AggregateEventEntry(
                event.identifier(),
                event.type().name(),
                event.type().version(),
                event.payloadAs(byte[].class, converter),
                converter.convert(event.metadata(), byte[].class),
                event.timestamp(),
                resolveAggregateType(tags),
                resolveAggregateIdentifier(tags),
                aggregateIdentifier != null ? aggregateSequencer.incrementAndGetSequenceOf(aggregateIdentifier) : null
        );
    }

    @Override
    public MessageStream<EventMessage> source(@Nonnull SourcingCondition condition, @Nullable ProcessingContext processingContext) {
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
        long firstSequenceNumber = condition.start();
        //noinspection DataFlowIssue
        StreamSpliterator<? extends AggregateEventEntry> entrySpliterator = new StreamSpliterator<>(
                lastEntry -> transactionManager.fetchInTransaction(() -> queryEventsBy(
                        aggregateIdentifier,
                        lastEntry != null && lastEntry.aggregateSequenceNumber() != null
                                ? lastEntry.aggregateSequenceNumber() + 1
                                : firstSequenceNumber
                )),
                finalBatchPredicate
        );

        MessageStream<EventMessage> source =
                MessageStream.fromStream(StreamSupport.stream(entrySpliterator, false),
                                         this::convertToEventMessage,
                                         entry -> setMarkerAndBuildContext(entry, markerReference))
                             // Defaults the marker when the aggregate stream was empty
                             .whenComplete(() -> markerReference.compareAndSet(
                                     null, new AggregateBasedConsistencyMarker(aggregateIdentifier, 0)
                             ))
                             .cast();
        return new AggregateSource(markerReference, source);
    }

    List<AggregateEventEntry> queryEventsBy(String aggregateIdentifier, long firstSequenceNumber) {
        try (EntityManager entityManager = entityManager()) {
            return entityManager.createQuery(EVENTS_BY_AGGREGATE_QUERY, AggregateEventEntry.class)
                                .setParameter("id", aggregateIdentifier)
                                .setParameter("seq", firstSequenceNumber)
                                .setMaxResults(batchSize)
                                .getResultList();
        }
    }

    private static Context setMarkerAndBuildContext(AggregateEventEntry entry,
                                                    AtomicReference<AggregateBasedConsistencyMarker> markerReference) {
        String aggregateId = Objects.requireNonNullElse(entry.aggregateIdentifier(), entry.aggregateIdentifier());
        String aggregateType = entry.aggregateType();
        Long aggregateSeqNo = Objects.requireNonNullElse(entry.aggregateSequenceNumber(), 0L);
        markerReference.set(new AggregateBasedConsistencyMarker(aggregateId, aggregateSeqNo));
        return buildContext(aggregateId, aggregateSeqNo, aggregateType);
    }

    private static ConsistencyMarker combineAggregateMarkers(Stream<AggregateSource> resultStream) {
        return resultStream.map(AggregateSource::markerReference)
                           .map(AtomicReference::get)
                           .map(marker -> (ConsistencyMarker) marker)
                           .reduce(ConsistencyMarker::upperBound)
                           .orElseThrow();
    }

    @Override
    public MessageStream<EventMessage> stream(@Nonnull StreamingCondition condition, @Nullable ProcessingContext processingContext) {
        GapAwareTrackingToken trackingToken = tokenOperations.assertGapAwareTrackingToken(condition.position());
        StreamSpliterator<? extends TokenAndEvent> entrySpliterator = new StreamSpliterator<>(
                lastItem -> queryTokensAndEventsBy(lastItem == null ? trackingToken : lastItem.token()),
                batch -> BATCH_OPTIMIZATION_DISABLED
        );

        return MessageStream.fromStream(StreamSupport.stream(entrySpliterator, false),
                                        tokenEntry -> convertToEventMessage(tokenEntry.event()),
                                        AggregateBasedJpaEventStorageEngine::buildTrackedContext);
    }

    private List<TokenAndEvent> queryTokensAndEventsBy(TrackingToken start) {
        Assert.isTrue(
                start == null || start instanceof GapAwareTrackingToken,
                () -> String.format("Token [%s] is of the wrong type. Expected [%s]",
                                    start, GapAwareTrackingToken.class.getSimpleName())
        );
        List<TokenAndEvent> result = new ArrayList<>();
        GapAwareTrackingToken cleanedToken = cleanedToken((GapAwareTrackingToken) start);
        List<AggregateEventEntry> events =
                transactionManager.fetchInTransaction(() -> queryEventsBy(cleanedToken));

        GapAwareTrackingToken token = cleanedToken;
        Instant gapTimeoutThreshold = tokenOperations.gapTimeoutThreshold();
        for (AggregateEventEntry event : events) {
            token = calculateToken(token, event.globalIndex(), event.timestamp(), gapTimeoutThreshold);
            result.add(new TokenAndEvent(token, event));
        }
        return result;
    }

    private GapAwareTrackingToken cleanedToken(GapAwareTrackingToken lastToken) {
        return lastToken != null && lastToken.getGaps().size() > gapCleaningThreshold
                ? tokenOperations.withGapsCleaned(lastToken, indexAndTimestampBetweenGaps(lastToken))
                : lastToken;
    }

    private List<Object[]> indexAndTimestampBetweenGaps(GapAwareTrackingToken token) {
        return transactionManager.fetchInTransaction(() -> {
            try (EntityManager entityManager = entityManager()) {
                return entityManager.createQuery(INDEX_AND_TIMESTAMP_QUERY, Object[].class)
                                    .setParameter("firstGapOffset", token.getGaps().first())
                                    .setParameter("maxGlobalIndex", token.getGaps().last() + 1L)
                                    .getResultList();
            }
        });
    }

    private List<AggregateEventEntry> queryEventsBy(GapAwareTrackingToken token) {
        try (EntityManager entityManager = entityManager()) {
            TypedQuery<AggregateEventEntry> eventsByTokenQuery =
                    token == null || token.getGaps().isEmpty()
                            ? entityManager.createQuery(EVENTS_BY_TOKEN_QUERY, AggregateEventEntry.class)
                            : entityManager.createQuery(EVENTS_BY_GAPPED_TOKEN, AggregateEventEntry.class)
                                           .setParameter("gaps", token.getGaps());

            return eventsByTokenQuery.setParameter("token", token == null ? -1L : token.getIndex())
                                     .setMaxResults(batchSize)
                                     .getResultList();
        }
    }

    private GapAwareTrackingToken calculateToken(@Nullable GapAwareTrackingToken token,
                                                 long globalIndex,
                                                 @Nonnull Instant timestamp,
                                                 @Nonnull Instant gapTimeoutThreshold) {
        boolean allowGaps = timestamp.isAfter(gapTimeoutThreshold);
        return token == null
                ? GapAwareTrackingToken.newInstance(globalIndex, calculateGaps(globalIndex, allowGaps))
                : token.advanceTo(globalIndex, allowGaps ? maxGapOffset : 0);
    }

    @Nonnull
    private Collection<Long> calculateGaps(long globalIndex, boolean allowGaps) {
        return allowGaps
                ? LongStream.range(Math.min(lowestGlobalSequence, globalIndex), globalIndex)
                            .boxed()
                            .collect(Collectors.toCollection(TreeSet::new))
                : Collections.emptySortedSet();
    }

    private GenericEventMessage convertToEventMessage(AggregateEventEntry event) {
        return new GenericEventMessage(event.identifier(),
                                       new MessageType(event.type(), event.version()),
                                       event.payload(),
                                       converter.convert(event.metadata(), METADATA_MAP_TYPE_REF.getType()),
                                       event.timestamp());
    }

    private static Context buildTrackedContext(@Nonnull TokenAndEvent tokenAndEvent) {
        AggregateEventEntry entry = tokenAndEvent.event();
        Context context = buildContext(Objects.requireNonNullElse(entry.aggregateIdentifier(), entry.identifier()),
                                       Objects.requireNonNullElse(entry.aggregateSequenceNumber(), 0L),
                                       entry.aggregateType());
        return context.withResource(TrackingToken.RESOURCE_KEY, tokenAndEvent.token);
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
    public CompletableFuture<TrackingToken> firstToken(@Nullable ProcessingContext processingContext) {
        return queryToken(FIRST_TOKEN_QUERY);
    }

    @Override
    public CompletableFuture<TrackingToken> latestToken(@Nullable ProcessingContext processingContext) {
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
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at, @Nullable ProcessingContext processingContext) {
        try (EntityManager entityManager = entityManager()) {
            List<Long> results = entityManager.createQuery(TOKEN_AT_QUERY, Long.class)
                                              .setParameter("dateTime", formatInstant(at))
                                              .getResultList();
            if (results.isEmpty() || results.getFirst() == null) {
                return latestToken(processingContext);
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
    }

    /**
     * A tuple of an {@link AtomicReference} to an {@link AggregateBasedConsistencyMarker} and a {@link MessageStream},
     * used when sourcing events from an aggregate-specific {@link EventCriterion}. This tuple object can then be used
     * to {@link MessageStream#concatWith(MessageStream) construct a single stream}, completing with a final marker.
     */
    private record AggregateSource(
            AtomicReference<AggregateBasedConsistencyMarker> markerReference,
            MessageStream<EventMessage> source
    ) {

    }

    private record TokenAndEvent(GapAwareTrackingToken token, AggregateEventEntry event) {

    }
}
