package org.axonframework.eventsourcing.eventstore.jpa;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Assert;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackedDomainEventData;
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
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.messaging.ClassBasedMessageNameResolver;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageNameResolver;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
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
import java.util.concurrent.Executor;
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
    private final Serializer snapshotSerializer;
    private final PersistenceExceptionResolver persistenceExceptionResolver;
    private final EventUpcaster upcasterChain;
    private final SnapshotFilter snapshotFilter;
    private final int batchSize;

    private final boolean explicitFlush;
    private final long lowestGlobalSequence;
    private final int maxGapOffset;
    private final int gapTimeout;

    // AsyncEventStorageEngine specific
    private final PersistenceExceptionMapper persistenceExceptionMapper;
    private final MessageNameResolver messageNameResolver;
    private final LegacyJpaEventStorageOperations legacyJpaOperations;
    private final BatchingEventStorageOperations batchingOperations;
    private final GapAwareTrackingTokenOperations tokenOperations;
    private Executor writeExecutor;

    public LegacyJpaEventStorageEngine(
            @javax.annotation.Nonnull EntityManagerProvider entityManagerProvider,
            @javax.annotation.Nonnull TransactionManager transactionManager,
            @javax.annotation.Nonnull Serializer eventSerializer,
            @javax.annotation.Nonnull Serializer snapshotSerializer,
            @javax.annotation.Nonnull UnaryOperator<Config> configurationOverride
    ) {
        this.writeExecutor = Executors.newFixedThreadPool(10); // todo: configurable, AxonThreadFactory etc.
        // todo: is is useful? Maybe we'd like to utilize the same thread as invoker?

        this.entityManagerProvider = entityManagerProvider;
        this.transactionManager = transactionManager;
        this.eventSerializer = eventSerializer;
        this.snapshotSerializer = snapshotSerializer;

        var customization = configurationOverride.apply(Config.defaultConfig());
        this.upcasterChain = customization.upcasterChain();
        this.persistenceExceptionResolver = customization.persistenceExceptionResolver();
        this.snapshotFilter = customization.snapshotFilter();
        this.batchSize = customization.batchSize();
        this.messageNameResolver = customization.messageNameResolver();

        // BatchingEventStorageEngine
        // customization.batchSize();
        // customization.finalAggregateBatchPredicate();

        // JpaEventStorageEngine
        this.explicitFlush = customization.explicitFlush();
        this.lowestGlobalSequence = customization.lowestGlobalSequence();
        this.maxGapOffset = customization.maxGapOffset();
        this.gapTimeout = customization.gapTimeout();

        this.legacyJpaOperations = new LegacyJpaEventStorageOperations(transactionManager,
                                                                       entityManagerProvider.getEntityManager(),
                                                                       domainEventEntryEntityName(),
                                                                       SnapshotEventEntry.class.getSimpleName());
        this.tokenOperations = new GapAwareTrackingTokenOperations(gapTimeout, logger);
        this.batchingOperations = new BatchingEventStorageOperations(
                transactionManager,
                legacyJpaOperations,
                tokenOperations,
                batchSize,
                customization.finalAggregateBatchPredicate(),
                true,
                customization.gapCleaningThreshold(),
                customization.lowestGlobalSequence(),
                customization.maxGapOffset()
        );
        this.persistenceExceptionMapper = new PersistenceExceptionMapper(persistenceExceptionResolver);
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
        return CompletableFuture.supplyAsync(
                () -> appendTransaction(condition, events),
                writeExecutor // todo: is it needed?
        );
    }

    // todo: move it for some base class, copied from LegacyAxonServerEventStorageEngine
    private void assertValidTags(List<TaggedEventMessage<?>> events) {
        for (TaggedEventMessage<?> taggedEvent : events) {
            if (taggedEvent.tags().size() > 1) {
                throw new IllegalArgumentException(
                        "An Event Storage engine in Aggregate mode does not support multiple tags per event");
            }
        }
    }

    private AppendTransaction appendTransaction(AppendCondition condition, List<TaggedEventMessage<?>> events) {
        return new AppendTransaction() {

            private final AtomicBoolean finished = new AtomicBoolean(false);

            @Override
            public CompletableFuture<ConsistencyMarker> commit() {
                if (finished.getAndSet(true)) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Already committed or rolled back"));
                }
                var consistencyMarker = AggregateBasedConsistencyMarker.from(condition);
                var aggregateSequences = new HashMap<String, AtomicLong>();
                return transactionManager.fetchInTransaction(() -> {
                    try {
                        var entityManager = entityManagerProvider.getEntityManager();
                        events.stream()
                              .map(taggedEvent -> toDomainEventMessage(taggedEvent,
                                                                       consistencyMarker,
                                                                       aggregateSequences))
                              .map(domainEventMessage -> new DomainEventEntry(domainEventMessage, eventSerializer))
                              .forEach(entityManager::persist);
                        if (explicitFlush) {
                            entityManager.flush();
                        }
                        var newConsistencyMarker = consistencyMarker;
                        for (var aggSeq : aggregateSequences.entrySet()) {
                            newConsistencyMarker = newConsistencyMarker.forwarded(aggSeq.getKey(),
                                                                                  aggSeq.getValue().get());
                        }
                        return CompletableFuture.completedFuture(newConsistencyMarker);
                    } catch (Exception e) {
                        var appendException = AppendEventsTransactionRejectedException.conflictingEventsDetected(
                                consistencyMarker);
                        var legacyPersistenceException = persistenceExceptionMapper.mapPersistenceException(e,
                                                                                                            events.getFirst()
                                                                                                                  .event());
                        appendException.addSuppressed(legacyPersistenceException);
                        throw appendException; // fixme: changed behavior (legacy throws legacyPersistenceException), compare with LegacyAxonServerEventStorageEngine
                    }
                });
            }

            private DomainEventMessage<?> toDomainEventMessage(TaggedEventMessage<?> taggedEvent,
                                                               AggregateBasedConsistencyMarker consistencyMarker,
                                                               HashMap<String, AtomicLong> aggregateSequences) {
                var aggregateIdentifier = resolveAggregateIdentifier(taggedEvent.tags());
                var aggregateType = resolveAggregateType(taggedEvent.tags());
                var event = taggedEvent.event();
                var isAggregateEvent =
                        aggregateIdentifier != null && aggregateType != null && !taggedEvent.tags().isEmpty();
                if (isAggregateEvent) {
                    var nextSequence = resolveSequencer(
                            aggregateSequences,
                            aggregateIdentifier,
                            consistencyMarker
                    ).incrementAndGet();
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

            @Override
            public void rollback() {
                finished.set(true);
                // todo: is it good solution?
                // Previously I received: jakarta.persistence.TransactionRequiredException: No EntityManager with actual transaction available for current thread - cannot reliably process 'persist' call
                // No action needed for rollback as the transaction is managed by the TransactionManager
            }
        };
    }

    // todo: move it for some base class, copied from LegacyAxonServerEventStorageEngine
    // todo: how do I know (even if its only tag), that it's an aggregate id?
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

    // todo: move it for some base class, copied from LegacyAxonServerEventStorageEngine
    private static AtomicLong resolveSequencer(Map<String, AtomicLong> aggregateSequences, String aggregateIdentifier,
                                               AggregateBasedConsistencyMarker consistencyMarker) {
        return aggregateSequences.computeIfAbsent(aggregateIdentifier,
                                                  i -> new AtomicLong(consistencyMarker.positionOf(i)));
    }

    // todo: don't mind gaps
    // now support just one aggregate per source!
    // todo: support batches!!!
    // same as old:     public DomainEventStream readEvents(@Nonnull String aggregateIdentifier, long firstSequenceNumber) {

    @Override
    public MessageStream<EventMessage<?>> source(@Nonnull SourcingCondition condition) {
        MessageStream<EventMessage<?>> resultingStream = MessageStream.empty();

        // todo: fetch in single transaction or multiple???
        for (EventCriteria criterion : condition.criteria()) {
            var aggregateIdentifier = resolveAggregateIdentifier(criterion.tags());
            var events = batchingOperations.readEventData(aggregateIdentifier,
                                                          condition.start(),
                                                          condition.end());
            // todo: upcasting etc!!
            MessageStream<EventMessage<?>> aggregateEvents = MessageStream.fromStream(
                    events,
                    this::convertToMessage,
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

    private EventMessage<?> convertToMessage(DomainEventData<?> event) {
        var payload = event.getPayload();
        var qualifiedName = payload.getType().getName().split("\\.(?=[^.]*$)");
        var namespace = qualifiedName[0];
        var localName = qualifiedName[1];
        var revision = payload.getType().getRevision();
        var name = new QualifiedName(namespace,
                                     localName,
                                     revision == null ? "0.0.1" : revision); // todo: resolve qualified name
        var identifier = event.getEventIdentifier();
        var data = payload.getData();
        var metadata = event.getMetaData();
        MetaData metaData = eventSerializer.convert(metadata.getData(), MetaData.class);
        try { // todo: how to serialize / deserliazie the payload and metadata!?
            return new GenericEventMessage<>(
                    identifier,
                    name,
                    data,
                    metaData,
                    event.getTimestamp()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String domainEventEntryEntityName() {
        return DomainEventEntry.class.getSimpleName(); // todo: configurable
    }

    @Override
    public MessageStream<EventMessage<?>> stream(@Nonnull StreamingCondition condition) {
        var trackingToken = gapAwareTrackingTokenFrom(condition.position());
        var events = batchingOperations.readEventData(trackingToken);
//        var processed = upcastAndDeserializeTrackedEvents(events, eventSerializer, upcasterChain)
//                .filter(e -> e instanceof TrackedDomainEventData<?>)
//                .map(e -> (TrackedDomainEventData<?>) e);
        return MessageStream.fromStream(
                events,
                this::convertToMessage,
                LegacyJpaEventStorageEngine::fillContextWith
        );
    }

    private static Context fillContextWith(DomainEventData<?> event) {
        var isAggregateEvent = event.getAggregateIdentifier() != null && event.getType() != null;
        if (isAggregateEvent) {
            return Context.with(LegacyResources.AGGREGATE_IDENTIFIER_KEY, event.getAggregateIdentifier())
                          .withResource(LegacyResources.AGGREGATE_TYPE_KEY, event.getType())
                          .withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY,
                                        event.getSequenceNumber())
                          .withResource(ConsistencyMarker.RESOURCE_KEY,
                                        new AggregateBasedConsistencyMarker(event.getAggregateIdentifier(),
                                                                            event.getSequenceNumber()));
        }
        return Context.empty(); // todo: what to do with that?
    }

    @Override
    public CompletableFuture<TrackingToken> tailToken() {
        var token = legacyJpaOperations.minGlobalIndex()
                                       .flatMap(this::gapAwareTrackingTokenOn)
                                       .orElse(null);
        return CompletableFuture.completedFuture(token); // todo: supplyAsync?
    }

    private GapAwareTrackingToken gapAwareTrackingTokenFrom(TrackingToken trackingToken) {
        if (trackingToken instanceof GapAwareTrackingToken gapAwareTrackingToken) {
            return gapAwareTrackingToken;
        }
        // todo: is it OK to get Gap aware tracking token from global one? or throw exception?
        return GapAwareTrackingToken.newInstance(trackingToken.position().orElse(lowestGlobalSequence),
                                                 Collections.emptySet());
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
        return CompletableFuture.completedFuture(headToken); // todo: supplyAsync?
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at) {
        var token = legacyJpaOperations.globalIndexAt(at)
                                       .flatMap(this::gapAwareTrackingTokenOn)
                                       .or(() -> legacyJpaOperations.maxGlobalIndex()
                                                                    .flatMap(this::gapAwareTrackingTokenOn))
                                       .orElse(null);
        return CompletableFuture.completedFuture(token); // todo: supplyAsync?
    }

    @Override
    public void describeTo(@javax.annotation.Nonnull ComponentDescriptor descriptor) {
        // todo: describe all fields (configuration)!
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

    private record PersistenceExceptionMapper(PersistenceExceptionResolver persistenceExceptionResolver) {

        RuntimeException mapPersistenceException(Exception exception, EventMessage<?> failedEvent) {
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

        /**
         * Build an exception message based on an EventMessage.
         * todo: what to do!?!?!
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

        // todo: changed TrackedDomainEventData, check if its good, maybe should be TrackedEventData and handled on upper level
        Stream<? extends TrackedDomainEventData<?>> readEventData(TrackingToken trackingToken) {
            EventStreamSpliterator<? extends TrackedDomainEventData<?>> spliterator = new EventStreamSpliterator<>(
                    lastItem -> fetchTrackedEvents(lastItem == null ? trackingToken : lastItem.trackingToken(),
                                                   batchSize),
                    batch -> BATCH_OPTIMIZATION_DISABLED
            );
            return StreamSupport.stream(spliterator, false);
        }

        // todo: changed TrackedDomainEventData, check if its good, maybe should be TrackedEventData and handled on upper level
        private List<? extends TrackedDomainEventData<?>> fetchTrackedEvents(TrackingToken lastToken, int batchSize) {
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


    public record Config(
            EventUpcaster upcasterChain,
            PersistenceExceptionResolver persistenceExceptionResolver,
            SnapshotFilter snapshotFilter,
            int batchSize,
            Predicate<List<? extends DomainEventData<?>>> finalAggregateBatchPredicate, // todo: handle!,
            MessageNameResolver messageNameResolver,
            boolean explicitFlush,
            int maxGapOffset,
            long lowestGlobalSequence,
            int gapTimeout,
            int gapCleaningThreshold
    ) {

        private static final int DEFAULT_BATCH_SIZE = 100;
        private static final int DEFAULT_MAX_GAP_OFFSET = 10000;
        private static final long DEFAULT_LOWEST_GLOBAL_SEQUENCE = 1;
        private static final int DEFAULT_GAP_TIMEOUT = 60000;
        private static final int DEFAULT_GAP_CLEANING_THRESHOLD = 250;

        public Config {
            assertNonNull(upcasterChain, "EventUpcaster may not be null");
            assertNonNull(snapshotFilter, "The snapshotFilter may not be null");
            assertThat(batchSize, size -> size > 0, "The batchSize must be a positive number");
            assertNonNull(messageNameResolver, "MessageNameResolver may not be null");
            assertPositive(maxGapOffset, "maxGapOffset");
            assertThat(lowestGlobalSequence,
                       number -> number > 0,
                       "The lowestGlobalSequence must be a positive number");
            assertPositive(gapTimeout, "gapTimeout");
            assertPositive(gapCleaningThreshold, "gapCleaningThreshold");
        }

        public static Config defaultConfig() {
            return new Config(
                    NoOpEventUpcaster.INSTANCE,
                    null,
                    SnapshotFilter.allowAll(),
                    DEFAULT_BATCH_SIZE,
                    null,
                    new ClassBasedMessageNameResolver(),
                    true,
                    DEFAULT_MAX_GAP_OFFSET,
                    DEFAULT_LOWEST_GLOBAL_SEQUENCE,
                    DEFAULT_GAP_TIMEOUT,
                    DEFAULT_GAP_CLEANING_THRESHOLD
            );
        }

        public Config upcasterChain(EventUpcaster upcasterChain) {
            return new Config(upcasterChain,
                              persistenceExceptionResolver,
                              snapshotFilter,
                              batchSize,
                              finalAggregateBatchPredicate,
                              messageNameResolver,
                              explicitFlush,
                              maxGapOffset,
                              lowestGlobalSequence,
                              gapTimeout,
                              gapCleaningThreshold
            );
        }

        public Config persistenceExceptionResolver(PersistenceExceptionResolver persistenceExceptionResolver) {
            return new Config(upcasterChain,
                              persistenceExceptionResolver,
                              snapshotFilter,
                              batchSize,
                              finalAggregateBatchPredicate,
                              messageNameResolver,
                              explicitFlush,
                              maxGapOffset,
                              lowestGlobalSequence,
                              gapTimeout,
                              gapCleaningThreshold
            );
        }

        public Config snapshotFilter(SnapshotFilter snapshotFilter) {
            return new Config(upcasterChain,
                              persistenceExceptionResolver,
                              snapshotFilter,
                              batchSize,
                              finalAggregateBatchPredicate,
                              messageNameResolver,
                              explicitFlush,
                              maxGapOffset,
                              lowestGlobalSequence,
                              gapTimeout,
                              gapCleaningThreshold
            );
        }

        public Config batchSize(int batchSize) {
            return new Config(upcasterChain,
                              persistenceExceptionResolver,
                              snapshotFilter,
                              batchSize,
                              finalAggregateBatchPredicate,
                              messageNameResolver,
                              explicitFlush,
                              maxGapOffset,
                              lowestGlobalSequence,
                              gapTimeout,
                              gapCleaningThreshold
            );
        }

        public Config finalAggregateBatchPredicate(
                Predicate<List<? extends DomainEventData<?>>> finalAggregateBatchPredicate) {
            return new Config(upcasterChain,
                              persistenceExceptionResolver,
                              snapshotFilter,
                              batchSize,
                              finalAggregateBatchPredicate,
                              messageNameResolver,
                              explicitFlush,
                              maxGapOffset,
                              lowestGlobalSequence,
                              gapTimeout,
                              gapCleaningThreshold
            );
        }

        public Config messageNameResolver(MessageNameResolver messageNameResolver) {
            return new Config(upcasterChain,
                              persistenceExceptionResolver,
                              snapshotFilter,
                              batchSize,
                              finalAggregateBatchPredicate,
                              messageNameResolver,
                              explicitFlush,
                              maxGapOffset,
                              lowestGlobalSequence,
                              gapTimeout,
                              gapCleaningThreshold
            );
        }

        // todo: other functions!
    }
}




