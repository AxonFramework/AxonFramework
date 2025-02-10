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
import org.axonframework.common.Assert;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventData;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GenericTrackedDomainEventMessage;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.TrackedDomainEventData;
import org.axonframework.eventhandling.TrackedEventData;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AggregateBasedConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.EmptyAppendTransaction;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.LegacyAggregateBasedEventStorageEngineUtils;
import org.axonframework.eventsourcing.eventstore.LegacyResources;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.StreamingCondition;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.*;
import static org.axonframework.common.BuilderUtils.*;
import static org.axonframework.common.ObjectUtils.getOrDefault;
import static org.axonframework.eventsourcing.eventstore.LegacyAggregateBasedEventStorageEngineUtils.*;
import static org.axonframework.eventsourcing.eventstore.LegacyAggregateBasedEventStorageEngineUtils.resolveAggregateIdentifier;


/**
 * EventStorageEngine implementation that uses JPA to store and fetch events.
 * <p>
 * By default, the payload of events is stored as a serialized blob of bytes. Other columns are used to store meta-data
 * that allow quick finding of DomainEvents for a specific aggregate in the correct order.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class LegacyJpaEventStorageEngine implements AsyncEventStorageEngine {

    private static final Logger logger = LoggerFactory.getLogger(LegacyJpaEventStorageEngine.class);
    private static final String DOMAIN_EVENT_ENTRY_ENTITY_NAME = DomainEventEntry.class.getSimpleName();

    private final EntityManagerProvider entityManagerProvider;
    private final TransactionManager transactionManager;
    private final Serializer eventSerializer;
    private final PersistenceExceptionResolver persistenceExceptionResolver;

    private final LegacyJpaEventStorageOperations legacyJpaOperations;
    private final BatchingEventStorageOperations batchingOperations;
    private final GapAwareTrackingTokenOperations tokenOperations;

    public LegacyJpaEventStorageEngine(
            @Nonnull EntityManagerProvider entityManagerProvider,
            @Nonnull TransactionManager transactionManager,
            @Nonnull Serializer eventSerializer,
            @Nonnull UnaryOperator<Customization> configurationOverride
    ) {
        this.entityManagerProvider = requireNonNull(entityManagerProvider, "entityManagerProvider may not be null");
        this.transactionManager = requireNonNull(transactionManager, "transactionManager may not be null");
        this.eventSerializer = requireNonNull(eventSerializer, "eventSerializer may not be null");

        var customization = requireNonNull(configurationOverride, "configurationOverride may not be null")
                .apply(Customization.withDefaultValues());

        this.legacyJpaOperations = new LegacyJpaEventStorageOperations(transactionManager,
                                                                       entityManagerProvider,
                                                                       DOMAIN_EVENT_ENTRY_ENTITY_NAME,
                                                                       "unused");
        this.tokenOperations = new GapAwareTrackingTokenOperations(
                customization.tokenGapsHandling().timeout(),
                logger
        );
        this.batchingOperations = new BatchingEventStorageOperations(
                transactionManager,
                legacyJpaOperations,
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
            private final AggregateBasedConsistencyMarker preCommitConsistencyMarker = AggregateBasedConsistencyMarker.from(
                    condition);

            @Override
            public CompletableFuture<ConsistencyMarker> commit() {
                if (txFinished.getAndSet(true)) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Already committed or rolled back"));
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
                return txResult
                        .exceptionallyCompose(e -> CompletableFuture.failedFuture(translateConflictException(e)))
                        .thenApply(r -> afterCommitConsistencyMarker);
            }

            private Throwable translateConflictException(Throwable e) {
                Predicate<Throwable> isConflictException = (t) -> persistenceExceptionResolver != null
                        && t instanceof Exception ex
                        && persistenceExceptionResolver.isDuplicateKeyViolation(ex);
                return LegacyAggregateBasedEventStorageEngineUtils
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
        var entityManager = entityManagerProvider.getEntityManager();
        events.stream()
              .map(taggedEvent -> toDomainEventMessage(taggedEvent, aggregateSequencer))
              .map(domainEventMessage -> new DomainEventEntry(domainEventMessage, eventSerializer))
              .forEach(entityManager::persist);
    }

    private static DomainEventMessage<?> toDomainEventMessage(
            TaggedEventMessage<?> taggedEvent,
            AggregateSequencer aggregateSequencer
    ) {
        var aggregateIdentifier = resolveAggregateIdentifier(taggedEvent.tags());
        var aggregateType = resolveAggregateType(taggedEvent.tags());
        var event = taggedEvent.event();
        var isAggregateEvent =
                aggregateIdentifier != null && aggregateType != null && !taggedEvent.tags().isEmpty();
        if (isAggregateEvent) {
            var nextSequence = aggregateSequencer.incrementAndGetSequenceOf(aggregateIdentifier);
            return new GenericDomainEventMessage<>(
                    aggregateType,
                    aggregateIdentifier,
                    nextSequence,
                    event.getIdentifier(),
                    event.type(),
                    event.getPayload(),
                    event.getMetaData(),
                    event.getTimestamp()
            );
        } else {
            // returns non-aggregate event, so the sequence is always 0
            return new GenericDomainEventMessage<>(null,
                                                   event.getIdentifier(),
                                                   0L,
                                                   event,
                                                   event::getTimestamp);
        }
    }

    private GenericDomainEventMessage<?> convertToDomainEventMessage(DomainEventData<?> event) {
        return new GenericDomainEventMessage<>(
                event.getType(),
                event.getAggregateIdentifier(),
                event.getSequenceNumber(),
                convertToEventMessage(event),
                event.getTimestamp()
        );
    }

    private GenericEventMessage<?> convertToEventMessage(EventData<?> event) {
        var payload = event.getPayload();
        var revision = payload.getType().getRevision();
        var payloadClass = eventSerializer.classForType(payload.getType());
        var messageType = revision == null
                ? new MessageType(payloadClass)
                : new MessageType(payloadClass, revision);
        var metadata = event.getMetaData();
        MetaData metaData = eventSerializer.convert(metadata.getData(), MetaData.class);
        return new GenericEventMessage<>(
                event.getEventIdentifier(),
                messageType,
                payload.getData(),
                metaData,
                event.getTimestamp()
        );
    }

    @Override
    public MessageStream<EventMessage<?>> source(@Nonnull SourcingCondition condition) {
        var allCriteriaStream = condition
                .criteria()
                .stream()
                .map(criteria -> this.eventsForCriteria(condition, criteria))
                .reduce(MessageStream.empty(), MessageStream::concatWith);

        var consistencyMarker = new AtomicReference<ConsistencyMarker>();
        return allCriteriaStream.map(e -> {
            var newMarker = consistencyMarker
                    .accumulateAndGet(
                            e.getResource(ConsistencyMarker.RESOURCE_KEY),
                            (m1, m2) -> m1 == null ? m2 : m1.upperBound(m2)
                    );
            return e.withResource(ConsistencyMarker.RESOURCE_KEY, newMarker);
        });
    }

    private MessageStream<EventMessage<?>> eventsForCriteria(SourcingCondition condition,
                                                             EventCriteria criterion) {
        var aggregateIdentifier = resolveAggregateIdentifier(criterion.tags());
        var events = batchingOperations.readEventData(
                aggregateIdentifier,
                condition.start(),
                condition.end()
        );
        return MessageStream.fromStream(
                events,
                this::convertToDomainEventMessage,
                LegacyJpaEventStorageEngine::domainEventContext
        );
    }

    private static Context domainEventContext(DomainEventData<?> event) {
        return Context.with(LegacyResources.AGGREGATE_IDENTIFIER_KEY, event.getAggregateIdentifier())
                      .withResource(LegacyResources.AGGREGATE_TYPE_KEY, event.getType())
                      .withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY, event.getSequenceNumber())
                      .withResource(
                              ConsistencyMarker.RESOURCE_KEY,
                              new AggregateBasedConsistencyMarker(event.getAggregateIdentifier(),
                                                                  event.getSequenceNumber())
                      );
    }

    @Override
    public MessageStream<EventMessage<?>> stream(@Nonnull StreamingCondition condition) {
        var trackingToken = tokenOperations.assertGapAwareTrackingToken(condition.position());
        var events = batchingOperations.readEventData(trackingToken);
        return MessageStream.fromStream(
                events,
                this::convertToTrackedEventMessage,
                LegacyJpaEventStorageEngine::trackedEventContext
        );
    }

    private TrackedEventMessage<?> convertToTrackedEventMessage(TrackedEventData<?> event) {
        var trackingToken = event.trackingToken();
        if (event instanceof TrackedDomainEventData<?> trackedDomainEventData) {
            var domainEventMessage = convertToDomainEventMessage(trackedDomainEventData);
            return new GenericTrackedDomainEventMessage<>(trackingToken, domainEventMessage);
        }
        return new GenericTrackedEventMessage<>(trackingToken, convertToEventMessage(event));
    }

    private static Context trackedEventContext(TrackedEventData<?> trackedEventData) {
        var context = Context.empty();
        if (trackedEventData instanceof TrackedDomainEventData<?> trackedDomainEventData
                && trackedDomainEventData.getAggregateIdentifier() != null
                && trackedDomainEventData.getType() != null
        ) {
            context = domainEventContext(trackedDomainEventData);
        }
        var trackingToken = trackedEventData.trackingToken();
        return context.withResource(TrackingToken.RESOURCE_KEY, trackingToken);
    }

    @Override
    public CompletableFuture<TrackingToken> tailToken() {
        var token = legacyJpaOperations.minGlobalIndex()
                                       .flatMap(this::gapAwareTrackingTokenOn)
                                       .orElse(null);
        return CompletableFuture.completedFuture(token);
    }

    private Optional<TrackingToken> gapAwareTrackingTokenOn(Long globalIndex) {
        return globalIndex == null
                ? Optional.empty()
                : Optional.of(GapAwareTrackingToken.newInstance(globalIndex, Collections.emptySet()));
    }

    @Override
    public CompletableFuture<TrackingToken> headToken() {
        var headToken = legacyJpaOperations.maxGlobalIndex()
                                           .flatMap(this::gapAwareTrackingTokenOn)
                                           .orElse(null);
        return CompletableFuture.completedFuture(headToken);
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at) {
        var token = legacyJpaOperations.globalIndexAt(at)
                                       .flatMap(this::gapAwareTrackingTokenOn)
                                       .or(() -> legacyJpaOperations.maxGlobalIndex()
                                                                    .flatMap(this::gapAwareTrackingTokenOn))
                                       .orElse(null);
        return CompletableFuture.completedFuture(token);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("entityManagerProvider", entityManagerProvider);
        descriptor.describeProperty("transactionManager", transactionManager);
        descriptor.describeProperty("eventSerializer", eventSerializer);
        descriptor.describeProperty("persistenceExceptionResolver", persistenceExceptionResolver);
        descriptor.describeProperty("legacyJpaOperations", legacyJpaOperations);
        descriptor.describeProperty("tokenOperations", tokenOperations);
        descriptor.describeProperty("batchingOperations", batchingOperations);
    }

    private record BatchingEventStorageOperations(TransactionManager transactionManager,
                                                  LegacyJpaEventStorageOperations legacyJpaOperations,
                                                  GapAwareTrackingTokenOperations tokenOperations,
                                                  int batchSize,
                                                  Predicate<List<? extends DomainEventData<?>>> finalAggregateBatchPredicate,
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
                Predicate<List<? extends DomainEventData<?>>> finalAggregateBatchPredicate,
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

        Stream<? extends DomainEventData<?>> readEventData(String identifier, long firstSequenceNumber,
                                                           long lastSequenceNumber) {
            EventStreamSpliterator<? extends DomainEventData<?>> spliterator = new EventStreamSpliterator<>(
                    lastItem -> transactionManager.fetchInTransaction(
                            () -> legacyJpaOperations.fetchDomainEvents(
                                    identifier,
                                    lastItem == null ? firstSequenceNumber : lastItem.getSequenceNumber() + 1,
                                    lastSequenceNumber,
                                    batchSize)
                    )
                    , finalAggregateBatchPredicate);
            return StreamSupport.stream(spliterator, false);
        }

        Stream<? extends TrackedEventData<?>> readEventData(TrackingToken trackingToken) {
            EventStreamSpliterator<? extends TrackedEventData<?>> spliterator = new EventStreamSpliterator<>(
                    lastItem -> fetchTrackedEvents(lastItem == null ? trackingToken : lastItem.trackingToken(),
                                                   batchSize),
                    batch -> BATCH_OPTIMIZATION_DISABLED
            );
            return StreamSupport.stream(spliterator, false);
        }

        private List<? extends TrackedEventData<?>> fetchTrackedEvents(TrackingToken lastToken, int batchSize) {
            Assert.isTrue(
                    lastToken == null || lastToken instanceof GapAwareTrackingToken,
                    () -> String.format("Token [%s] is of the wrong type. Expected [%s]",
                                        lastToken, GapAwareTrackingToken.class.getSimpleName())
            );

            GapAwareTrackingToken previousToken = cleanedToken((GapAwareTrackingToken) lastToken);

            List<Object[]> entries = transactionManager.fetchInTransaction(() -> legacyJpaOperations.fetchEvents(
                    previousToken,
                    batchSize));
            return legacyJpaOperations.entriesToEvents(
                    previousToken, entries, tokenOperations.gapTimeoutThreshold(), lowestGlobalSequence, maxGapOffset
            );
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


        private boolean defaultFinalAggregateBatchPredicate(List<? extends DomainEventData<?>> recentBatch) {
            return fetchForAggregateUntilEmpty() ? recentBatch.isEmpty() : recentBatch.size() < batchSize;
        }

        private static class EventStreamSpliterator<T> extends Spliterators.AbstractSpliterator<T> {

            private final Function<T, List<? extends T>> fetchFunction;
            private final Predicate<List<? extends T>> finalBatchPredicate;

            private Iterator<? extends T> iterator;
            private T lastItem;
            private boolean lastBatchFound;

            private EventStreamSpliterator(Function<T, List<? extends T>> fetchFunction,
                                           Predicate<List<? extends T>> finalBatchPredicate) {
                super(Long.MAX_VALUE, NONNULL | ORDERED | DISTINCT | CONCURRENT);
                this.fetchFunction = fetchFunction;
                this.finalBatchPredicate = finalBatchPredicate;
            }

            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                requireNonNull(action);
                if (iterator == null || !iterator.hasNext()) {
                    if (lastBatchFound) {
                        return false;
                    }
                    List<? extends T> items = fetchFunction.apply(lastItem);
                    lastBatchFound = finalBatchPredicate.test(items);
                    iterator = items.iterator();
                }
                if (!iterator.hasNext()) {
                    return false;
                }

                action.accept(lastItem = iterator.next());
                return true;
            }
        }
    }


    public record Customization(
            PersistenceExceptionResolver persistenceExceptionResolver,
            int batchSize,
            Predicate<List<? extends DomainEventData<?>>> finalAggregateBatchPredicate,
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
                Predicate<List<? extends DomainEventData<?>>> finalAggregateBatchPredicate
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
                    tokenGapsHandling
            );
        }
    }
}
