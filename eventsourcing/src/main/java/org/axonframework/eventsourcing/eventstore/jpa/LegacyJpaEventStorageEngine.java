package org.axonframework.eventsourcing.eventstore.jpa;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import org.axonframework.common.Assert;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.TrackedEventData;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AggregateBasedConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.AppendEventsTransactionRejectedException;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.eventsourcing.eventstore.LegacyResources;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.StreamingCondition;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.eventsourcing.eventstore.TooManyTagsOnEventMessageException;
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.messaging.ClassBasedMessageNameResolver;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageNameResolver;
import org.axonframework.messaging.MessageStream;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.*;
import static org.axonframework.common.ObjectUtils.getOrDefault;
import static org.axonframework.eventhandling.EventUtils.upcastAndDeserializeTrackedEvents;
import static org.axonframework.eventsourcing.EventStreamUtils.upcastAndDeserializeDomainEvents;


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

    private final EntityManagerProvider entityManagerProvider;
    private final TransactionManager transactionManager;
    private final Serializer eventSerializer;
    private final EventUpcaster upcasterChain;

    // todo: snapshots support
    private final Serializer snapshotSerializer;
    private final SnapshotFilter snapshotFilter;

    private final boolean explicitFlush;

    private final PersistenceExceptionMapper persistenceExceptionMapper;
    private final MessageNameResolver messageNameResolver; // todo: use while upcasting implementing MessageName
    private final LegacyJpaEventStorageOperations legacyJpaOperations;
    private final BatchingEventStorageOperations batchingOperations;
    private final GapAwareTrackingTokenOperations tokenOperations;

    public LegacyJpaEventStorageEngine(
            @javax.annotation.Nonnull EntityManagerProvider entityManagerProvider,
            @javax.annotation.Nonnull TransactionManager transactionManager,
            @javax.annotation.Nonnull Serializer eventSerializer,
            @javax.annotation.Nonnull Serializer snapshotSerializer,
            @javax.annotation.Nonnull UnaryOperator<Customization> configurationOverride
    ) {
        this.entityManagerProvider = entityManagerProvider;
        this.transactionManager = transactionManager;
        this.eventSerializer = eventSerializer;
        this.snapshotSerializer = snapshotSerializer;

        var customization = configurationOverride.apply(Customization.withDefaultValues());
        this.upcasterChain = customization.upcasterChain();
        this.snapshotFilter = customization.snapshotFilter();
        this.messageNameResolver = customization.messageNameResolver();
        this.explicitFlush = customization.explicitFlush();

        this.legacyJpaOperations = new LegacyJpaEventStorageOperations(transactionManager,
                                                                       entityManagerProvider.getEntityManager(),
                                                                       domainEventEntryEntityName(),
                                                                       SnapshotEventEntry.class.getSimpleName());
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
        this.persistenceExceptionMapper = new PersistenceExceptionMapper(customization.persistenceExceptionResolver());
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
            return CompletableFuture.completedFuture(new EmptyAppendTransaction(condition));
        }

        var consistencyMarker = AggregateBasedConsistencyMarker.from(condition);
        var aggregateSequencer = AggregateSequencer.with(consistencyMarker);

//        var tx = transactionManager.startTransaction();
//        try {
//            entityManagerPersistEvents(aggregateSequencer, events);
//            if (explicitFlush) {
//                entityManagerProvider.getEntityManager().flush();
//            }
//        } catch (Exception e) {
//            tx.rollback();
//            return CompletableFuture.failedFuture(e);
//        }

        return CompletableFuture.completedFuture(new AppendTransaction() {

            @Override
            public CompletableFuture<ConsistencyMarker> commit() {
                CompletableFuture<Void> txResult = new CompletableFuture<>();
                var tx = transactionManager.startTransaction();
                try {
                    entityManagerPersistEvents(aggregateSequencer, events);
                    if (explicitFlush) {
                        entityManagerProvider.getEntityManager().flush();
                    }
                    tx.commit();
                    txResult.complete(null);
                } catch (Exception e) {
                    tx.rollback();
                    txResult.completeExceptionally(e);
                }

                var finalConsistencyMarker = aggregateSequencer.forwarded();
                return txResult.exceptionallyCompose(e -> CompletableFuture.failedFuture(
                                     appendEventsTransactionRejectedExceptionFrom(e, condition, events)))
                             .thenApply(r -> finalConsistencyMarker);
            }

            @Override
            public void rollback() {
//                tx.rollback();
            }
        });
    }

    // todo: describe changed behavior (legacy throws legacyPersistenceException), compare with LegacyAxonServerEventStorageEngine
    private AppendEventsTransactionRejectedException appendEventsTransactionRejectedExceptionFrom(Throwable suppressed,
                                                                                                  AppendCondition appendCondition,
                                                                                                  List<TaggedEventMessage<?>> events) {
        var consistencyMarker = AggregateBasedConsistencyMarker.from(appendCondition);
        var appendException = AppendEventsTransactionRejectedException.conflictingEventsDetected(consistencyMarker);
        var legacyPersistenceException = persistenceExceptionMapper.mapPersistenceException(suppressed,
                                                                                            events.getFirst().event());
        appendException.addSuppressed(legacyPersistenceException);
        return appendException;
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

    private void assertValidTags(List<TaggedEventMessage<?>> events) {
        for (TaggedEventMessage<?> taggedEvent : events) {
            if (taggedEvent.tags().size() > 1) {
                throw new TooManyTagsOnEventMessageException(
                        "An Event Storage engine in Aggregate mode does not support multiple tags per event",
                        taggedEvent.event(),
                        taggedEvent.tags());
            }
        }
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
            var nextSequence = aggregateSequencer.resolveBy(aggregateIdentifier).incrementAndGet();
            return new GenericDomainEventMessage<>(
                    aggregateType,
                    aggregateIdentifier,
                    nextSequence,
                    event.getIdentifier(),
                    event.name(),
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

    // todo: move it for some base class, copied from LegacyAxonServerEventStorageEngine
    @Nullable
    private static String resolveAggregateIdentifier(Set<Tag> tags) {
        if (tags.isEmpty()) {
            return null;
        } else if (tags.size() > 1) {
            throw new IllegalArgumentException("Condition must provide exactly one tag");
        } else {
            return tags.iterator().next().value();
        }
    }

    // todo: move it for some base class, copied from LegacyAxonServerEventStorageEngine
    @Nullable
    private static String resolveAggregateType(Set<Tag> tags) {
        if (tags.isEmpty()) {
            return null;
        } else if (tags.size() > 1) {
            throw new IllegalArgumentException("Condition must provide exactly one tag");
        } else {
            return tags.iterator().next().key();
        }
    }

    @Override
    public MessageStream<EventMessage<?>> source(@Nonnull SourcingCondition condition) {
        MessageStream<EventMessage<?>> resultingStream = MessageStream.empty();
        for (EventCriteria criterion : condition.criteria()) {
            var aggregateIdentifier = resolveAggregateIdentifier(criterion.tags());
            var events = batchingOperations.readEventData(
                    aggregateIdentifier,
                    condition.start(),
                    condition.end()
            );
            var deserialized = upcastAndDeserializeDomainEvents(events, eventSerializer, upcasterChain);

            MessageStream<EventMessage<?>> aggregateEvents = MessageStream.fromStream(
                    deserialized.asStream(),
                    e -> e,
                    LegacyJpaEventStorageEngine::fillContextWith
            );
            resultingStream = resultingStream.concatWith(aggregateEvents);
        }
        AtomicReference<ConsistencyMarker> consistencyMarker = new AtomicReference<>();
        return resultingStream.map(e -> {
            ConsistencyMarker newMarker = consistencyMarker.accumulateAndGet(e.getResource(ConsistencyMarker.RESOURCE_KEY),
                                                                             (m1, m2) -> m1
                                                                                     == null ? m2 : m1.upperBound(m2));
            return e.withResource(ConsistencyMarker.RESOURCE_KEY, newMarker);
        });
    }

    private String domainEventEntryEntityName() {
        return DomainEventEntry.class.getSimpleName();
    }

    @Override
    public MessageStream<EventMessage<?>> stream(@Nonnull StreamingCondition condition) {
        var trackingToken = tokenOperations.assertGapAwareTrackingToken(condition.position());
        var events = batchingOperations.readEventData(trackingToken);
        var deserialized = upcastAndDeserializeTrackedEvents(events, eventSerializer, upcasterChain);
        return MessageStream.fromStream(
                deserialized,
                e -> e,
                LegacyJpaEventStorageEngine::fillContextWith
        );
    }

    private static Context fillContextWith(Message<?> event) {
        var context = Context.empty();
        if (event instanceof DomainEventMessage<?> trackedDomainEventData
                && trackedDomainEventData.getAggregateIdentifier() != null
                && trackedDomainEventData.getType() != null) {
            context = context.withResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY,
                                           trackedDomainEventData.getAggregateIdentifier())
                             .withResource(LegacyResources.AGGREGATE_TYPE_KEY, trackedDomainEventData.getType())
                             .withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY,
                                           trackedDomainEventData.getSequenceNumber())
                             .withResource(ConsistencyMarker.RESOURCE_KEY,
                                           new AggregateBasedConsistencyMarker(trackedDomainEventData.getAggregateIdentifier(),
                                                                               trackedDomainEventData.getSequenceNumber()));
        }
        if (event instanceof TrackedEventMessage<?> trackedEventMessage) {
            var token = trackedEventMessage.trackingToken();
            context = Context.with(TrackingToken.RESOURCE_KEY, token);
        }
        return context;
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
    public void describeTo(@javax.annotation.Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("entityManagerProvider", entityManagerProvider);
        descriptor.describeProperty("transactionManager", transactionManager);
        descriptor.describeProperty("eventSerializer", eventSerializer);
        descriptor.describeProperty("upcasterChain", upcasterChain);
        descriptor.describeProperty("snapshotSerializer", snapshotSerializer);
        descriptor.describeProperty("snapshotFilter", snapshotFilter);
        descriptor.describeProperty("explicitFlush", explicitFlush);
        descriptor.describeProperty("persistenceExceptionMapper", persistenceExceptionMapper);
        descriptor.describeProperty("messageNameResolver", messageNameResolver);
        descriptor.describeProperty("legacyJpaOperations", legacyJpaOperations);
        descriptor.describeProperty("tokenOperations", tokenOperations);
        descriptor.describeProperty("batchingOperations", batchingOperations);
    }

    private record EmptyAppendTransaction(AppendCondition appendCondition) implements AppendTransaction {

        @Override
        public CompletableFuture<ConsistencyMarker> commit() {
            return CompletableFuture.completedFuture(AggregateBasedConsistencyMarker.from(appendCondition));
        }

        @Override
        public void rollback() {

        }
    }

    static final class AggregateSequencer {

        private final Map<String, AtomicLong> aggregateSequences;
        private AggregateBasedConsistencyMarker consistencyMarker;

        AggregateSequencer(Map<String, AtomicLong> aggregateSequences,
                           AggregateBasedConsistencyMarker consistencyMarker) {
            this.aggregateSequences = aggregateSequences;
            this.consistencyMarker = consistencyMarker;
        }

        public static AggregateSequencer with(AggregateBasedConsistencyMarker consistencyMarker) {
            return new AggregateSequencer(new HashMap<>(), consistencyMarker);
        }

        AggregateBasedConsistencyMarker forwarded() {
            var newConsistencyMarker = consistencyMarker;
            for (var aggSeq : aggregateSequences.entrySet()) {
                newConsistencyMarker = newConsistencyMarker
                        .forwarded(aggSeq.getKey(), aggSeq.getValue().get());
            }
            consistencyMarker = newConsistencyMarker;
            return newConsistencyMarker;
        }

        private AtomicLong resolveBy(String aggregateIdentifier) {
            return aggregateSequences.computeIfAbsent(aggregateIdentifier,
                                                      i -> new AtomicLong(consistencyMarker.positionOf(i)));
        }
    }

    private record PersistenceExceptionMapper(PersistenceExceptionResolver persistenceExceptionResolver) {

        Throwable mapPersistenceException(Throwable throwable, EventMessage<?> failedEvent) {
            if (throwable instanceof Exception exception) {
                String eventDescription = buildExceptionMessage(failedEvent);
                if (persistenceExceptionResolver != null
                        && persistenceExceptionResolver.isDuplicateKeyViolation(exception)) {
                    if (isFirstDomainEvent(failedEvent)) {
                        return new AggregateStreamCreationException(eventDescription, exception);
                    }
                    return new ConcurrencyException(eventDescription, exception);
                } else {
                    return new EventStoreException(eventDescription, exception);
                }
            }
            return throwable;
        }

        /**
         * Build an exception message based on an EventMessage.
         *
         * @param failedEvent the event to be used for the exception message
         * @return the created exception message
         */
        private String buildExceptionMessage(EventMessage<?> failedEvent) {
            String eventDescription = format("An event with identifier [%s] could not be persisted",
                                             failedEvent.getIdentifier());
            if (isFirstDomainEvent(failedEvent)) {
                DomainEventMessage<?> failedDomainEvent = (DomainEventMessage<?>) failedEvent;
                eventDescription = format(
                        "Cannot reuse aggregate identifier [%s] to create aggregate [%s] since identifiers need to be unique.",
                        failedDomainEvent.getAggregateIdentifier(),
                        failedDomainEvent.getType());
            } else if (failedEvent instanceof DomainEventMessage<?> failedDomainEvent) {
                eventDescription = format("An event for aggregate [%s] at sequence [%d] was already inserted",
                                          failedDomainEvent.getAggregateIdentifier(),
                                          failedDomainEvent.getSequenceNumber());
            }
            return eventDescription;
        }

        /**
         * Check whether or not this is the first event, which means we tried to create an aggregate through the given
         * {@code failedEvent}.
         *
         * @param failedEvent the event to be checked
         * @return true in case of first event, false otherwise
         */
        private boolean isFirstDomainEvent(EventMessage<?> failedEvent) {
            return failedEvent instanceof DomainEventMessage<?> domainEvent
                    && domainEvent.getSequenceNumber() == 0L;
        }
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
                return tokenOperations.withGapsCleaned(lastToken, indexToTimestamp(lastToken));
            }
            return lastToken;
        }

        private List<Object[]> indexToTimestamp(GapAwareTrackingToken lastToken) {
            return transactionManager.fetchInTransaction(() -> legacyJpaOperations.indexToTimestamp(lastToken));
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
                Objects.requireNonNull(action);
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
            EventUpcaster upcasterChain,
            PersistenceExceptionResolver persistenceExceptionResolver,
            SnapshotFilter snapshotFilter,
            int batchSize,
            Predicate<List<? extends DomainEventData<?>>> finalAggregateBatchPredicate,
            MessageNameResolver messageNameResolver,
            boolean explicitFlush,
            long lowestGlobalSequence,
            TokenGapsHandlingConfig tokenGapsHandling
    ) {

        private static final int DEFAULT_BATCH_SIZE = 100;
        private static final long DEFAULT_LOWEST_GLOBAL_SEQUENCE = 1;

        public Customization {
            assertNonNull(upcasterChain, "EventUpcaster may not be null");
            assertNonNull(snapshotFilter, "The snapshotFilter may not be null");
            assertThat(batchSize, size -> size > 0, "The batchSize must be a positive number");
            assertNonNull(messageNameResolver, "MessageNameResolver may not be null");
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
                    NoOpEventUpcaster.INSTANCE,
                    null,
                    SnapshotFilter.allowAll(),
                    DEFAULT_BATCH_SIZE,
                    null,
                    new ClassBasedMessageNameResolver(),
                    true,
                    DEFAULT_LOWEST_GLOBAL_SEQUENCE,
                    TokenGapsHandlingConfig.withDefaultValues()
            );
        }

        public Customization upcasterChain(EventUpcaster upcasterChain) {
            return new Customization(upcasterChain,
                                     persistenceExceptionResolver,
                                     snapshotFilter,
                                     batchSize,
                                     finalAggregateBatchPredicate,
                                     messageNameResolver,
                                     explicitFlush,
                                     lowestGlobalSequence,
                                     tokenGapsHandling
            );
        }

        public Customization persistenceExceptionResolver(PersistenceExceptionResolver persistenceExceptionResolver) {
            return new Customization(upcasterChain,
                                     persistenceExceptionResolver,
                                     snapshotFilter,
                                     batchSize,
                                     finalAggregateBatchPredicate,
                                     messageNameResolver,
                                     explicitFlush,
                                     lowestGlobalSequence,
                                     tokenGapsHandling
            );
        }

        public Customization snapshotFilter(SnapshotFilter snapshotFilter) {
            return new Customization(upcasterChain,
                                     persistenceExceptionResolver,
                                     snapshotFilter,
                                     batchSize,
                                     finalAggregateBatchPredicate,
                                     messageNameResolver,
                                     explicitFlush,
                                     lowestGlobalSequence,
                                     tokenGapsHandling
            );
        }

        public Customization batchSize(int batchSize) {
            return new Customization(upcasterChain,
                                     persistenceExceptionResolver,
                                     snapshotFilter,
                                     batchSize,
                                     finalAggregateBatchPredicate,
                                     messageNameResolver,
                                     explicitFlush,
                                     lowestGlobalSequence,
                                     tokenGapsHandling
            );
        }

        public Customization finalAggregateBatchPredicate(
                Predicate<List<? extends DomainEventData<?>>> finalAggregateBatchPredicate) {
            return new Customization(upcasterChain,
                                     persistenceExceptionResolver,
                                     snapshotFilter,
                                     batchSize,
                                     finalAggregateBatchPredicate,
                                     messageNameResolver,
                                     explicitFlush,
                                     lowestGlobalSequence,
                                     tokenGapsHandling
            );
        }

        public Customization messageNameResolver(MessageNameResolver messageNameResolver) {
            return new Customization(upcasterChain,
                                     persistenceExceptionResolver,
                                     snapshotFilter,
                                     batchSize,
                                     finalAggregateBatchPredicate,
                                     messageNameResolver,
                                     explicitFlush,
                                     lowestGlobalSequence,
                                     tokenGapsHandling
            );
        }

        public Customization lowestGlobalSequence(long lowestGlobalSequence) {
            return new Customization(upcasterChain,
                                     persistenceExceptionResolver,
                                     snapshotFilter,
                                     batchSize,
                                     finalAggregateBatchPredicate,
                                     messageNameResolver,
                                     explicitFlush,
                                     lowestGlobalSequence,
                                     tokenGapsHandling
            );
        }

        public Customization tokenGapsHandling(UnaryOperator<TokenGapsHandlingConfig> configurationOverride) {
            return new Customization(upcasterChain,
                                     persistenceExceptionResolver,
                                     snapshotFilter,
                                     batchSize,
                                     finalAggregateBatchPredicate,
                                     messageNameResolver,
                                     explicitFlush,
                                     lowestGlobalSequence,
                                     configurationOverride.apply(tokenGapsHandling)
            );
        }

        public Customization explicitFlush(boolean explicitFlush) {
            return new Customization(upcasterChain,
                                     persistenceExceptionResolver,
                                     snapshotFilter,
                                     batchSize,
                                     finalAggregateBatchPredicate,
                                     messageNameResolver,
                                     explicitFlush,
                                     lowestGlobalSequence,
                                     tokenGapsHandling
            );
        }
    }
}




